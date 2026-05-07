# ApRbot API Examples

Reference implementations for interacting with ApRbot services.

## Structure

```
apis/
├── python/aprbot/          # Python 3.10+ examples
│   ├── dreamagent/         # DreamAgent (tickets, KB, agents)
│   ├── dreamdocs/          # DreamDocs (upload, OCR, predict)
│   ├── dreamocr/           # DreamOCR (PDF recognition)
│   ├── dreamvoice/         # DreamVoice (STT/transcription)
│   └── aimos/              # AIMoS (LLM, chat, embeddings)
├── java/aprbot/            # Java 11+ examples
│   ├── dreamagent/
│   ├── dreamdocs/
│   ├── dreamocr/
│   ├── dreamvoice/
│   └── aimos/
└── test_files/             # Sample files for testing
```

## Quick Start

Every example is self-contained and can be run directly:

```bash
# Python
cd python/aprbot/dreamagent
python tickets_example.py --setup --title "Test" --description "Hello"

# Java
cd java/aprbot/dreamagent
make test-tickets
```

## Default Host

All examples default to **`test.dreamdocs.ru`** (a placeholder domain).

Override via CLI flag or environment variable:

```bash
# CLI flag
python tickets_example.py --host http://my-server.com:7000

# Environment variable
export AGENT_HOST=http://my-server.com:7000
python tickets_example.py --setup
```

## Service Ports

| Service | Default Port | Env Var |
|---------|-------------|---------|
| DreamAgent | 7000 | `AGENT_HOST` |
| DreamDocs | 9000 | `DOCS_HOST` |
| DreamVoice | 2000 | `VOICE_HOST` |
| DreamOCR | 9900 | `OCR_HOST` |
| AIMOS | 4100 | `AIMOS_HOST` |

## Authentication

Examples use pre-configured demo credentials. Override via env vars:

```bash
export AGENT_USER=your-user
export AGENT_PASS=your-password
```

### API Keys

Some services require an `API-KEY` header:

| Service | Default Key | Env Var |
|---------|-------------|---------|
| DreamOCR | `demo-key` | `OCR_KEY` |
| DreamVoice | `voice-local-test-key-2026` | `VOICE_KEY` |

## `--setup` Flag

Most examples support `--setup` to auto-provision resources:

- **DreamAgent**: auto-selects first workspace, creates `Demo Project` if none exist
- **DreamDocs**: auto-selects first docset, creates `Demo Project` docset if none exist

## Requirements

**Python:**
- Python 3.10+
- `requests` (most examples)
- `python-dotenv` (optional)

**Java:**
- Java 11+
- Maven
- Jackson Databind 2.15.2

```bash
cd java && mvn compile -q
```

## License

Copyright (c) 2023 DocuTable Inc. / ApRbot
