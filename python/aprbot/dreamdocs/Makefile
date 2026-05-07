# DreamDocs Python API Examples

# Load local env vars if .env exists
ifneq (,$(wildcard .env))
    include .env
    export
endif


PYTHON   ?= python3
TEST_DIR ?= ../../../test_files

.PHONY: params  help test-predict test-search test-setup test-ocr test-all

help:
	@echo "DreamDocs API Examples"
	@echo ""
	@echo "Targets:"
	@echo "  test-predict   Upload file and predict fields"
	@echo "  test-search    Search documents"
	@echo "  test-setup     List/create docsets and doctypes"
	@echo "  test-ocr       Run OCR via DreamDocs"
	@echo "  test-all       Run all examples"
	@echo ""
	@echo "Env vars: DOCS_HOST, DOCS_USER, DOCS_PASS"
params:
	@echo "DOCS_HOST: $(DOCS_HOST)"
	@echo "DOCS_USER: $(DOCS_USER)"
	@echo "DOCS_PASS: $(DOCS_PASS)"


test-predict:
	@echo "=== DreamDocs Predict Test ==="
	$(PYTHON) predict_example.py \
		-i $(TEST_DIR)/test_document.pdf \
		-o /tmp/aprbot_test_docs/ \
		--host $(DOCS_HOST) \
		-u $(DOCS_USER) \
		-p $(DOCS_PASS) \
		--setup

test-search:
	@echo "=== DreamDocs Search Test ==="
	$(PYTHON) search_example.py \
		--host $(DOCS_HOST) \
		-u $(DOCS_USER) \
		-p $(DOCS_PASS)

test-setup:
	@echo "=== DreamDocs Setup Test ==="
	$(PYTHON) setup_example.py \
		--host $(DOCS_HOST) \
		-u $(DOCS_USER) \
		-p $(DOCS_PASS)

test-ocr:
	@echo "=== DreamDocs OCR Test ==="
	$(PYTHON) ocr_example.py \
		-i $(TEST_DIR)/test_image.png \
		-o /tmp/aprbot_test_docs_ocr/ \
		--host $(DOCS_HOST) \
		-u $(DOCS_USER) \
		-p $(DOCS_PASS) \
		--project-id 1

test-all: test-predict test-search test-setup test-ocr
