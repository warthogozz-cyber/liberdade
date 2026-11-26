import type { SaveReceiptItem } from '@/shared/api/markets'

export type ProductUnit = SaveReceiptItem['unit']
export const PRODUCT_UNITS: ProductUnit[] = ['UN', 'KG', 'G', 'L', 'ML', 'CX', 'PCT', 'DZ']

/** The longest product or market name the API accepts. */
export const MAX_PRODUCT_NAME = 80
