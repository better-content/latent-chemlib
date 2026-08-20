# Changelog

## Unreleased

- Normalize the project identity to `latent-chemlib / latent_chemlib`; this is a clean break with no legacy aliases or migrations.
- Hand released ChemLib gases directly to native AdPother pollutant blocks and
  leave ambient movement, wind, spreading, hazards, and explosions entirely to
  AdPother.
- Remove Latent-owned atmospheric cloud blocks and their duplicate scheduler,
  projection mixins, and custom gas-fireball behavior.
