/** Mirrors backend product/substitute/dto/SubstituteDtos.java exactly (CR-089). */

export type ProductRequestStatus = 'OPEN' | 'RESOLVED' | 'CANCELLED';
export type MatchLevel = 'EXCELLENT' | 'HIGH' | 'MEDIUM' | 'LOW' | 'DO_NOT_RECOMMEND';
export type SuggestionSource = 'RULE_BASED' | 'MANUAL_MAPPING';
export type RelationshipType = 'ALTERNATIVE' | 'COMPATIBLE' | 'UPGRADE' | 'LOWER_COST' | 'SAME_USE' | 'REPLACEMENT';

export interface CreateProductRequestBody {
  productId: number;
  requestedQuantity: number;
  requestedBudgetPaise?: number | null;
  customerName?: string | null;
  customerMobile?: string | null;
}

export interface SubstituteProductResponse {
  id: number;
  productCode: string;
  productName: string;
  categoryName?: string | null;
  brandName?: string | null;
  productType?: string | null;
  usageType?: string | null;
  sizeLabel?: string | null;
  material?: string | null;
  colorFinish?: string | null;
  shape?: string | null;
  unit: string;
  sellingPricePaise: number;
  sellingPriceDisplay: string;
  availableStock: number;
  inStock: boolean;
}

export interface SuggestionResponse {
  product: SubstituteProductResponse;
  score: number;
  maximumScore: number;
  matchLevel: MatchLevel;
  reason: string;
  source: SuggestionSource;
  aboveBudget: boolean;
}

export interface ProductRequestResponse {
  id: number;
  requestedProduct: SubstituteProductResponse;
  requestedQuantity: number;
  requestedBudgetPaise?: number | null;
  requestedBudgetDisplay?: string | null;
  customerName?: string | null;
  customerMobile?: string | null;
  status: ProductRequestStatus;
  requestedProductStock: number;
  selectedProduct?: SubstituteProductResponse | null;
  selectedAt?: string | null;
  createdAt: string;
  resolvedAt?: string | null;
  suggestions: SuggestionResponse[];
}

export interface ComparisonRow {
  label: string;
  requestedValue: string;
  alternativeValue: string;
  same: boolean;
}

export interface ComparisonResponse {
  requested: SubstituteProductResponse;
  alternative: SubstituteProductResponse;
  rows: ComparisonRow[];
}

export interface RelationshipResponse {
  id: number;
  relatedProduct: SubstituteProductResponse;
  relationshipType: RelationshipType;
  notes?: string | null;
  createdAt: string;
}

export interface CreateRelationshipBody {
  relatedProductId: number;
  relationshipType: RelationshipType;
  notes?: string | null;
}

export interface SubstituteSettingResponse {
  minScoreThreshold: number;
  showAboveBudget: boolean;
  maxResults: number;
}
