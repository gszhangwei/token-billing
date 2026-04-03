# The enhancement content

We need to enhance the billing engine to support diverse subscription strategies and variable, model-specific pricing. This requires several changes

- API enhancement: update the existing POST /api/usage endpoint to accept a new, required modelId parameter (e.g., “fast-model”, “reasoning-model”).
- Model-aware pricing: shift from a single global rate to dynamic pricing, where costs vary depending on the specific AI model invoked.
- Multi-plan billing logic: introduce distinct billing behaviors based on the customer's subscription tier:
  - Standard plan (optimized): retains the global monthly quota, but any overage usage is now calculated using model-specific rates.
  - Premium plan (new): operates without a quota limit. It introduces split billing, where prompt tokens and completion tokens are charged separately at different rates depending on the model used.
- Architectural scalability: implement an extensible design pattern (such as Strategy or Factory) to cleanly isolate the calculation formulas for different plans, ensuring the system can easily accommodate future pricing models.