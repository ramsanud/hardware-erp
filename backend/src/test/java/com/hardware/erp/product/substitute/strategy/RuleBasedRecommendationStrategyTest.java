package com.hardware.erp.product.substitute.strategy;

import com.hardware.erp.inventory.repository.StockRepository;
import com.hardware.erp.product.entity.Brand;
import com.hardware.erp.product.entity.Category;
import com.hardware.erp.product.entity.Product;
import com.hardware.erp.product.repository.ProductRepository;
import com.hardware.erp.product.substitute.config.SubstituteScoringProperties;
import com.hardware.erp.product.substitute.entity.MatchLevel;
import com.hardware.erp.product.substitute.entity.SuggestionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CR-089 §5/§6. The scoring rules themselves, with no database in the way.
 * The brief's own worked example is the fixture: a 4-inch MS tower bolt
 * requested, an SS 4-inch tower bolt and a 6-inch one as candidates.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RuleBasedRecommendationStrategyTest {

    @Mock private ProductRepository productRepository;
    @Mock private StockRepository stockRepository;

    private RuleBasedRecommendationStrategy strategy;
    private SubstituteScoringProperties weights;

    private Category doorFittings;
    private Brand brandA;
    private Brand brandB;

    @BeforeEach
    void setUp() {
        // Nulls everywhere - the record's compact constructor fills in the
        // brief's own defaults, which is exactly what production runs with.
        weights = new SubstituteScoringProperties(null, null, null, null, null, null, null, null, null);
        strategy = new RuleBasedRecommendationStrategy(productRepository, stockRepository, weights);

        doorFittings = Category.builder().id(1L).categoryName("Door Fittings").build();
        brandA = Brand.builder().id(1L).brandName("Brand A").build();
        brandB = Brand.builder().id(2L).brandName("Brand B").build();
    }

    private Product product(Long id, String name, Category category, Brand brand, String type,
                            String usage, String size, String material, long pricePaise) {
        return Product.builder()
                .id(id).productCode("PRD-" + id).productName(name)
                .category(category).brand(brand)
                .productType(type).usageType(usage).sizeLabel(size).material(material)
                .sellingPricePaise(pricePaise)
                .unit("PCS")
                .build();
    }

    @Test
    @DisplayName("the weights sum to the brief's documented maximum of 110")
    void maximumScoreIs110() {
        assertThat(weights.maximumScore()).isEqualTo(110);
    }

    @Test
    @DisplayName("an identical-but-for-material 4 inch tower bolt in stock scores High or better and says what differs")
    void nearIdenticalAlternativeScoresHigh() {
        Product requested = product(1L, "Tower Bolt 4 Inch", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L);
        Product candidate = product(2L, "SS Tower Bolt 4 Inch", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "Stainless Steel", 20_000L);

        ScoredSuggestion result = strategy.score(requested, candidate, new BigDecimal("12"));

        // category 30 + type 20 + usage 20 + size 15 + brand 5 + price 5 (within 20%) + stock 10 = 105
        assertThat(result.score()).isEqualTo(105);
        assertThat(result.matchLevel()).isEqualTo(MatchLevel.EXCELLENT);
        assertThat(result.source()).isEqualTo(SuggestionSource.RULE_BASED);
        assertThat(result.reason()).contains("Differs on").contains("material");
    }

    @Test
    @DisplayName("a same-family but wrong-size product scores lower and names the size as the difference")
    void differentSizeScoresLower() {
        Product requested = product(1L, "Tower Bolt 4 Inch", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L);
        Product candidate = product(3L, "Tower Bolt 6 Inch", doorFittings, brandA,
                "Tower Bolt", "Door Security", "6 Inch", "MS", 18_500L);

        ScoredSuggestion result = strategy.score(requested, candidate, new BigDecimal("5"));

        // Everything except size: 30 + 20 + 20 + 5 (material) + 5 (brand) + 5 (price) + 10 (stock) = 95
        assertThat(result.score()).isEqualTo(95);
        assertThat(result.reason()).contains("size");
        // ... and it must score strictly below the same-size candidate above.
        Product sameSize = product(2L, "SS Tower Bolt 4 Inch", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "Stainless Steel", 20_000L);
        assertThat(result.score()).isLessThan(strategy.score(requested, sameSize, new BigDecimal("12")).score());
    }

    @Test
    @DisplayName("two products that share only a category score far lower and are honest about having little in common")
    void categoryOnlyScoresLow() {
        Product requested = product(1L, "Tower Bolt 4 Inch", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L);
        Product candidate = product(4L, "Door Handle Oval", doorFittings, brandB,
                "Handle", "Door Opening", "Standard", "Aluminium", 45_000L);

        ScoredSuggestion result = strategy.score(requested, candidate, new BigDecimal("3"));

        // category 30 + stock 10 only - nothing else matches, price is far off.
        assertThat(result.score()).isEqualTo(40);
        assertThat(result.matchLevel()).isEqualTo(MatchLevel.LOW);
    }

    @Test
    @DisplayName("a missing attribute on either side never counts as a match - two blanks are two unknowns")
    void blankAttributesDoNotMatch() {
        Product requested = product(1L, "Tower Bolt 4 Inch", doorFittings, brandA,
                null, null, null, null, 18_000L);
        Product candidate = product(2L, "Some Other Bolt", doorFittings, brandA,
                null, null, null, null, 18_000L);

        ScoredSuggestion result = strategy.score(requested, candidate, new BigDecimal("10"));

        // category 30 + brand 5 + price 5 + stock 10 = 50. The four blank
        // attribute pairs contribute nothing.
        assertThat(result.score()).isEqualTo(50);
    }

    @Test
    @DisplayName("attribute comparison ignores case and surrounding whitespace")
    void attributeComparisonIsCaseAndWhitespaceInsensitive() {
        Product requested = product(1L, "Tower Bolt", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L);
        Product candidate = product(2L, "Tower Bolt II", doorFittings, brandA,
                "  tower bolt ", "DOOR SECURITY", "4 inch", " ms ", 18_000L);

        ScoredSuggestion result = strategy.score(requested, candidate, new BigDecimal("10"));

        assertThat(result.score()).isEqualTo(weights.maximumScore());
        assertThat(result.matchLevel()).isEqualTo(MatchLevel.EXCELLENT);
    }

    @Test
    @DisplayName("a product with stock still scores the in-stock bonus; one without it does not")
    void inStockBonusIsAppliedOnlyWhenStockExists() {
        Product requested = product(1L, "Tower Bolt 4 Inch", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L);
        Product candidate = product(2L, "SS Tower Bolt 4 Inch", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 18_000L);

        int withStock = strategy.score(requested, candidate, new BigDecimal("4")).score();
        int withoutStock = strategy.score(requested, candidate, BigDecimal.ZERO).score();

        assertThat(withStock - withoutStock).isEqualTo(weights.inStock());
    }

    @Test
    @DisplayName("a requested product with no price gives nothing away on price similarity rather than matching everything")
    void zeroPricedRequestScoresNoPriceSimilarity() {
        Product requested = product(1L, "Unpriced Item", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 0L);
        Product candidate = product(2L, "Priced Item", doorFittings, brandA,
                "Tower Bolt", "Door Security", "4 Inch", "MS", 0L);

        ScoredSuggestion result = strategy.score(requested, candidate, new BigDecimal("10"));

        assertThat(result.score()).isEqualTo(weights.maximumScore() - weights.similarPrice());
        assertThat(result.reason()).contains("price");
    }

    @Test
    @DisplayName("the match level bands follow the brief exactly")
    void matchLevelBands() {
        assertThat(MatchLevel.of(110)).isEqualTo(MatchLevel.EXCELLENT);
        assertThat(MatchLevel.of(90)).isEqualTo(MatchLevel.EXCELLENT);
        assertThat(MatchLevel.of(89)).isEqualTo(MatchLevel.HIGH);
        assertThat(MatchLevel.of(75)).isEqualTo(MatchLevel.HIGH);
        assertThat(MatchLevel.of(74)).isEqualTo(MatchLevel.MEDIUM);
        assertThat(MatchLevel.of(60)).isEqualTo(MatchLevel.MEDIUM);
        assertThat(MatchLevel.of(59)).isEqualTo(MatchLevel.LOW);
        assertThat(MatchLevel.of(40)).isEqualTo(MatchLevel.LOW);
        assertThat(MatchLevel.of(39)).isEqualTo(MatchLevel.DO_NOT_RECOMMEND);
        assertThat(MatchLevel.of(0)).isEqualTo(MatchLevel.DO_NOT_RECOMMEND);
    }
}
