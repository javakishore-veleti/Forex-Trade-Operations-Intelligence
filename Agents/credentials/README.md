# Credentials

This directory is reserved for n8n credential configuration references.

## ⚠️ No Credential Values

**No actual credential values, API keys, tokens, or secrets are stored in this directory or anywhere in this repository.**

Only credential _type_ references and synthetic placeholder examples are permitted.

## Synthetic Examples

```json
{
  "name": "FX-Platform-API-Key",
  "type": "httpHeaderAuth",
  "data": {
    "name": "X-API-Key",
    "value": "FX-SYNTHETIC-KEY-000001"
  }
}
```

All identifiers use the `FX-` prefix to indicate synthetic/test data.
