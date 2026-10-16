# Course Pricing Audit

## Current Architecture
- **Model**: Embedded directly in Course (price, salePrice).
- **Currency**: Implicitly INR (hardcoded in frontend).
- **Free vs Paid**: Checkbox in frontend toggles isFree which sets price = 0.

## Integration
- **Payment Backend**: Developer 1 utilizes price for Razorpay payment initialization.
- **Checkout**: Developer 3 reads price for student display.

## Problems Identified
- No tracking of price changes.
- Price can be negative or exorbitant if frontend validation is bypassed (backend lacks @Min constraints on price).

## Recommended Improvements
- Add @Min(0) to Course pricing fields.
- Document strict contract for D1 payment gateways.
