package com.hardware.erp.product.substitute.service.impl;

import com.hardware.erp.common.activity.ActivityLogService;
import com.hardware.erp.common.dto.PageResponse;
import com.hardware.erp.common.exception.BusinessException;
import com.hardware.erp.common.exception.DuplicateResourceException;
import com.hardware.erp.common.exception.ResourceNotFoundException;
import com.hardware.erp.common.util.IndianCurrencyFormat;
import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.substitute.config.SubstituteScoringProperties;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.ComparisonResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.ComparisonRow;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateProductRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.CreateRelationshipRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.ProductRequestResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.RelationshipResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SelectAlternativeRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteProductResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingRequest;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SubstituteSettingResponse;
import com.hardware.erp.product.substitute.dto.SubstituteDtos.SuggestionResponse;
import com.hardware.erp.product.substitute.entity.ProductRelationship;
import com.hardware.erp.product.substitute.entity.ProductRequestRecord;
import com.hardware.erp.product.substitute.entity.ProductRequestStatus;
import com.hardware.erp.product.substitute.entity.ProductRequestSuggestion;
import com.hardware.erp.product.substitute.entity.SubstituteSetting;
import com.hardware.erp.product.substitute.repository.ProductRelationshipRepository;
import com.hardware.erp.product.substitute.repository.ProductRequestRepository;
import com.hardware.erp.product.substitute.repository.ProductRequestSuggestionRepository;
import com.hardware.erp.product.substitute.repository.SubstituteSettingRepository;
import com.hardware.erp.product.substitute.service.SubstituteRecommendationService;
import com.hardware.erp.product.substitute.strategy.RecommendationStrategy;
import com.hardware.erp.product.substitute.strategy.ScoredSuggestion;
import com.hardware.erp.product.substitute.strategy.SubstituteContext;
import com.hardware.erp.security.SecurityUtils;
import com.hardware.erp.subscription.entity.FeatureKey;
import com.hardware.erp.subscription.service.FeatureAccessService;
import com.hardware.erp.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * CR-089. Orchestrates the strategies, applies the owner's policy
 * (threshold, budget, result count) and persists what was suggested.
 *
 * The order of operations matters and is the brief's own §23 flow:
 * category/type filtering -> attribute matching -> compatibility mapping ->
 * stock filtering -> price/budget filtering -> scoring -> top N. Stock and
 * category filtering happen in SQL (ProductRepository.findSubstituteCandidates)
 * before anything is scored, so a large catalogue costs one query.
 */
@Service
@RequiredArgsConstructor
public class SubstituteRecommendationServiceImpl implements SubstituteRecommendationService {

    private static final String MODULE = "PRODUCT";
    private static final String ENTITY = "PRODUCT_REQUEST";

    private final ProductRequestRepository requestRepository;
    private final ProductRequestSuggestionRepository suggestionRepository;
    private final ProductRelationshipRepository relationshipRepository;
    private final SubstituteSettingRepository settingRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final TenantRepository tenantRepository;
    private final ActivityLogService activityLog;
    private final FeatureAccessService featureAccessService;
    private final SubstituteScoringProperties scoringProperties;
    /** Spring injects every RecommendationStrategy bean; order is imposed by priority(), not by bean discovery order. */
    private final List<RecommendationStrategy> strategies;

    // ---------------------------------------------------------------
    // Product requests
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public ProductRequestResponse createRequest(CreateProductRequest request) {
        featureAccessService.requireFeature(FeatureKey.SMART_SUBSTITUTE);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Product requested = requireProduct(request.productId(), tenantId);

        ProductRequestRecord record = requestRepository.save(ProductRequestRecord.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .requestedProduct(requested)
                .requestedQuantity(request.requestedQuantity())
                .requestedBudgetPaise(request.requestedBudgetPaise())
                .customerName(blankToNull(request.customerName()))
                .customerMobile(blankToNull(request.customerMobile()))
                .status(ProductRequestStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .createdBy(SecurityUtils.currentUserId().orElse(null))
                .build());

        List<ProductRequestSuggestion> suggestions = computeAndStore(record, tenantId);

        Map<String, Object> logged = new LinkedHashMap<>();
        logged.put("product", requested.getProductName());
        logged.put("quantity", request.requestedQuantity());
        logged.put("suggestionCount", suggestions.size());
        activityLog.created(MODULE, ENTITY, record.getId(), requested.getProductName(), logged);

        return toResponse(record, suggestions, tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductRequestResponse get(Long requestId) {
        featureAccessService.requireFeature(FeatureKey.SMART_SUBSTITUTE);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ProductRequestRecord record = requireRequest(requestId, tenantId);
        return toResponse(record, suggestionRepository.findForRequest(requestId), tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductRequestResponse> search(ProductRequestStatus status, Pageable pageable) {
        featureAccessService.requireFeature(FeatureKey.SMART_SUBSTITUTE);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Page<ProductRequestRecord> page = requestRepository.search(tenantId, status, pageable);
        return PageResponse.from(page, record ->
                toResponse(record, suggestionRepository.findForRequest(record.getId()), tenantId));
    }

    @Override
    @Transactional
    public ProductRequestResponse recomputeAlternatives(Long requestId) {
        featureAccessService.requireFeature(FeatureKey.SMART_SUBSTITUTE);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ProductRequestRecord record = requireRequest(requestId, tenantId);
        if (record.getStatus() != ProductRequestStatus.OPEN) {
            throw new BusinessException("This request is already " + record.getStatus().name().toLowerCase()
                    + " - reopen it by recording a new request instead.");
        }
        return toResponse(record, computeAndStore(record, tenantId), tenantId);
    }

    @Override
    @Transactional
    public ProductRequestResponse selectAlternative(Long requestId, SelectAlternativeRequest request) {
        featureAccessService.requireFeature(FeatureKey.SMART_SUBSTITUTE);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ProductRequestRecord record = requireRequest(requestId, tenantId);
        if (record.getStatus() == ProductRequestStatus.CANCELLED) {
            throw new BusinessException("This request was cancelled.");
        }

        List<ProductRequestSuggestion> suggestions = suggestionRepository.findForRequest(requestId);
        // The chosen product must be one this shop was actually shown. An
        // arbitrary product id here would let the audit trail (§14) record a
        // "selected alternative" the engine never suggested, which is exactly
        // the analysis §25 is meant to make trustworthy.
        Product selected = suggestions.stream()
                .map(ProductRequestSuggestion::getSuggestedProduct)
                .filter(product -> product.getId().equals(request.productId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Choose one of the alternatives suggested for this request."));

        record.setSelectedProduct(selected);
        record.setSelectedBy(SecurityUtils.currentUserId().orElse(null));
        record.setSelectedAt(LocalDateTime.now());
        record.setStatus(ProductRequestStatus.RESOLVED);
        record.setResolvedAt(LocalDateTime.now());
        requestRepository.save(record);

        // §13/§24: recording the choice does NOT touch any invoice. Billing
        // continues separately with whatever the owner actually sells.
        activityLog.action(MODULE, ENTITY, record.getId(), record.getRequestedProduct().getProductName(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE,
                "Alternative selected: " + selected.getProductName());

        return toResponse(record, suggestions, tenantId);
    }

    @Override
    @Transactional
    public ProductRequestResponse cancel(Long requestId) {
        featureAccessService.requireFeature(FeatureKey.SMART_SUBSTITUTE);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ProductRequestRecord record = requireRequest(requestId, tenantId);
        if (record.getStatus() == ProductRequestStatus.RESOLVED) {
            throw new BusinessException("This request was already resolved - it cannot be cancelled afterwards.");
        }
        record.setStatus(ProductRequestStatus.CANCELLED);
        record.setResolvedAt(LocalDateTime.now());
        requestRepository.save(record);
        activityLog.action(MODULE, ENTITY, record.getId(), record.getRequestedProduct().getProductName(),
                com.hardware.erp.common.activity.ActivityAction.UPDATE, "Product request cancelled");
        return toResponse(record, suggestionRepository.findForRequest(requestId), tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public ComparisonResponse compare(Long requestId, Long alternativeProductId) {
        featureAccessService.requireFeature(FeatureKey.SMART_SUBSTITUTE);
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ProductRequestRecord record = requireRequest(requestId, tenantId);
        Product requested = record.getRequestedProduct();
        Product alternative = requireProduct(alternativeProductId, tenantId);

        List<ComparisonRow> rows = new ArrayList<>();
        rows.add(row("Product", requested.getProductName(), alternative.getProductName()));
        rows.add(row("Product code", requested.getProductCode(), alternative.getProductCode()));
        rows.add(row("Category", nameOfCategory(requested), nameOfCategory(alternative)));
        rows.add(row("Brand", nameOfBrand(requested), nameOfBrand(alternative)));
        rows.add(row("Type", requested.getProductType(), alternative.getProductType()));
        rows.add(row("Usage", requested.getUsageType(), alternative.getUsageType()));
        rows.add(row("Size", requested.getSizeLabel(), alternative.getSizeLabel()));
        rows.add(row("Material", requested.getMaterial(), alternative.getMaterial()));
        rows.add(row("Finish", requested.getColorFinish(), alternative.getColorFinish()));
        rows.add(row("Shape", requested.getShape(), alternative.getShape()));
        rows.add(row("Price", IndianCurrencyFormat.rupees(requested.getSellingPricePaise()),
                IndianCurrencyFormat.rupees(alternative.getSellingPricePaise())));
        rows.add(row("Stock", plain(stockOf(tenantId, requested.getId())),
                plain(stockOf(tenantId, alternative.getId()))));

        return new ComparisonResponse(toProductResponse(requested, tenantId),
                toProductResponse(alternative, tenantId), rows);
    }

    // ---------------------------------------------------------------
    // Manual relationships
    // ---------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<RelationshipResponse> relationshipsFor(Long productId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        requireProduct(productId, tenantId);
        return relationshipRepository.findForProduct(tenantId, productId).stream()
                .map(relationship -> new RelationshipResponse(
                        relationship.getId(),
                        toProductResponse(relationship.getRelatedProduct(), tenantId),
                        relationship.getRelationshipType(),
                        relationship.getNotes(),
                        relationship.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public RelationshipResponse createRelationship(Long productId, CreateRelationshipRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        Product product = requireProduct(productId, tenantId);
        if (Objects.equals(productId, request.relatedProductId())) {
            throw new BusinessException("A product cannot be an alternative to itself.");
        }
        Product related = requireProduct(request.relatedProductId(), tenantId);
        if (relationshipRepository.existsByTenantIdAndProductIdAndRelatedProductIdAndRelationshipType(
                tenantId, productId, request.relatedProductId(), request.relationshipType())) {
            throw new DuplicateResourceException("Relationship", related.getProductName());
        }

        ProductRelationship saved = relationshipRepository.save(ProductRelationship.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .product(product)
                .relatedProduct(related)
                .relationshipType(request.relationshipType())
                .notes(blankToNull(request.notes()))
                .createdAt(LocalDateTime.now())
                .createdBy(SecurityUtils.currentUserId().orElse(null))
                .build());

        activityLog.created(MODULE, "PRODUCT_RELATIONSHIP", saved.getId(), product.getProductName(),
                Map.of("relatedProduct", related.getProductName(),
                        "relationshipType", request.relationshipType().name()));

        return new RelationshipResponse(saved.getId(), toProductResponse(related, tenantId),
                saved.getRelationshipType(), saved.getNotes(), saved.getCreatedAt());
    }

    @Override
    @Transactional
    public void deleteRelationship(Long productId, Long relationshipId) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        ProductRelationship relationship = relationshipRepository.findByIdAndTenantId(relationshipId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Relationship", relationshipId));
        if (!relationship.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException("Relationship", relationshipId);
        }
        relationshipRepository.delete(relationship);
        activityLog.deleted(MODULE, "PRODUCT_RELATIONSHIP", relationshipId,
                relationship.getProduct().getProductName(), "Alternative mapping removed");
    }

    // ---------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SubstituteSettingResponse settings() {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        return toSettingResponse(settingFor(tenantId));
    }

    @Override
    @Transactional
    public SubstituteSettingResponse updateSettings(SubstituteSettingRequest request) {
        Long tenantId = SecurityUtils.requireCurrentTenantId();
        if (request.minScoreThreshold() > scoringProperties.maximumScore()) {
            throw new BusinessException("The minimum score cannot be above " + scoringProperties.maximumScore()
                    + ", which is the highest score any alternative can reach.");
        }
        SubstituteSetting setting = settingFor(tenantId);
        setting.setMinScoreThreshold(request.minScoreThreshold());
        setting.setShowAboveBudget(request.showAboveBudget());
        setting.setMaxResults(request.maxResults());
        setting.setUpdatedAt(LocalDateTime.now());
        setting.setUpdatedBy(SecurityUtils.currentUserId().orElse(null));
        return toSettingResponse(settingRepository.save(setting));
    }

    // ---------------------------------------------------------------

    /**
     * Runs every strategy in priority order, keeps the first suggestion for
     * any given product (so a manual mapping is never overwritten by the
     * generic score - §17), applies the owner's threshold, budget rule and
     * result limit, and replaces the stored suggestion set.
     */
    private List<ProductRequestSuggestion> computeAndStore(ProductRequestRecord record, Long tenantId) {
        SubstituteSetting setting = settingFor(tenantId);
        SubstituteContext context = new SubstituteContext(tenantId, record.getRequestedProduct(),
                record.getRequestedQuantity(), record.getRequestedBudgetPaise());

        Map<Long, ScoredSuggestion> byProductId = new LinkedHashMap<>();
        strategies.stream()
                .sorted(Comparator.comparingInt(RecommendationStrategy::priority))
                .forEach(strategy -> strategy.suggest(context)
                        .forEach(suggestion -> byProductId.putIfAbsent(suggestion.product().getId(), suggestion)));

        // §17: a manual mapping outranks ANY similarity score, so the sort
        // is source first, score second. Sorting by score alone would let a
        // near-identical-on-paper product (which can reach 110) push the
        // owner's own "this is the replacement" below it - caught by
        // ProductRequestIT.manualMappingOutranksSimilarity.
        List<ScoredSuggestion> shortlisted = byProductId.values().stream()
                .filter(suggestion -> suggestion.score() >= setting.getMinScoreThreshold())
                .filter(suggestion -> setting.isShowAboveBudget()
                        || !aboveBudget(suggestion, record.getRequestedBudgetPaise()))
                .sorted(Comparator.comparingInt((ScoredSuggestion suggestion) -> suggestion.source().rank())
                        .thenComparing(Comparator.comparingInt(ScoredSuggestion::score).reversed())
                        .thenComparing(suggestion -> suggestion.product().getProductName()))
                .limit(setting.getMaxResults())
                .toList();

        suggestionRepository.deleteForRequest(record.getId());
        suggestionRepository.flush();

        List<ProductRequestSuggestion> saved = new ArrayList<>(shortlisted.size());
        for (ScoredSuggestion suggestion : shortlisted) {
            saved.add(suggestionRepository.save(ProductRequestSuggestion.builder()
                    .productRequest(record)
                    .suggestedProduct(suggestion.product())
                    .score(suggestion.score())
                    .matchLevel(suggestion.matchLevel())
                    .reason(suggestion.reason())
                    .source(suggestion.source())
                    .createdAt(LocalDateTime.now())
                    .build()));
        }
        return saved;
    }

    private boolean aboveBudget(ScoredSuggestion suggestion, Long budgetPaise) {
        if (budgetPaise == null) {
            return false;
        }
        long price = suggestion.product().getSellingPricePaise() == null
                ? 0L : suggestion.product().getSellingPricePaise();
        return price > budgetPaise;
    }

    private SubstituteSetting settingFor(Long tenantId) {
        return settingRepository.findById(tenantId).orElseGet(() -> SubstituteSetting.defaults(tenantId));
    }

    private ProductRequestRecord requireRequest(Long requestId, Long tenantId) {
        return requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product request", requestId));
    }

    private Product requireProduct(Long productId, Long tenantId) {
        return productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private BigDecimal stockOf(Long tenantId, Long productId) {
        return stockRepository.findByTenantIdAndProductId(tenantId, productId)
                .map(stock -> stock.getQuantityOnHand())
                .orElse(BigDecimal.ZERO);
    }

    private ProductRequestResponse toResponse(ProductRequestRecord record,
                                              List<ProductRequestSuggestion> suggestions,
                                              Long tenantId) {
        Long budget = record.getRequestedBudgetPaise();
        List<SuggestionResponse> suggestionResponses = suggestions.stream()
                .map(suggestion -> {
                    Product product = suggestion.getSuggestedProduct();
                    long price = product.getSellingPricePaise() == null ? 0L : product.getSellingPricePaise();
                    return new SuggestionResponse(
                            toProductResponse(product, tenantId),
                            suggestion.getScore(),
                            scoringProperties.maximumScore(),
                            suggestion.getMatchLevel(),
                            suggestion.getReason(),
                            suggestion.getSource(),
                            budget != null && price > budget);
                })
                .toList();

        return new ProductRequestResponse(
                record.getId(),
                toProductResponse(record.getRequestedProduct(), tenantId),
                record.getRequestedQuantity(),
                budget,
                budget == null ? null : IndianCurrencyFormat.rupees(budget),
                record.getCustomerName(),
                record.getCustomerMobile(),
                record.getStatus(),
                stockOf(tenantId, record.getRequestedProduct().getId()),
                record.getSelectedProduct() == null ? null
                        : toProductResponse(record.getSelectedProduct(), tenantId),
                record.getSelectedAt(),
                record.getCreatedAt(),
                record.getResolvedAt(),
                suggestionResponses);
    }

    private SubstituteProductResponse toProductResponse(Product product, Long tenantId) {
        BigDecimal onHand = stockOf(tenantId, product.getId());
        long price = product.getSellingPricePaise() == null ? 0L : product.getSellingPricePaise();
        return new SubstituteProductResponse(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                nameOfCategory(product),
                nameOfBrand(product),
                product.getProductType(),
                product.getUsageType(),
                product.getSizeLabel(),
                product.getMaterial(),
                product.getColorFinish(),
                product.getShape(),
                product.getUnit(),
                price,
                IndianCurrencyFormat.rupees(price),
                onHand,
                onHand.compareTo(BigDecimal.ZERO) > 0);
    }

    private SubstituteSettingResponse toSettingResponse(SubstituteSetting setting) {
        return new SubstituteSettingResponse(setting.getMinScoreThreshold(),
                setting.isShowAboveBudget(), setting.getMaxResults());
    }

    private ComparisonRow row(String label, String requested, String alternative) {
        String left = requested == null || requested.isBlank() ? "—" : requested;
        String right = alternative == null || alternative.isBlank() ? "—" : alternative;
        // "Same" only when both sides actually carry a value - two blanks are
        // not agreement, they are two unknowns (same rule as the scorer's).
        boolean same = !"—".equals(left) && left.equalsIgnoreCase(right);
        return new ComparisonRow(label, left, right, same);
    }

    private String nameOfCategory(Product product) {
        return product.getCategory() == null ? null : product.getCategory().getCategoryName();
    }

    private String nameOfBrand(Product product) {
        return product.getBrand() == null ? null : product.getBrand().getBrandName();
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
