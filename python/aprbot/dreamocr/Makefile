# DreamOCR Python API Examples

# Load local env vars if .env exists
ifneq (,$(wildcard .env))
    include .env
    export
endif


PYTHON   ?= python3
TEST_DIR ?= ../../../test_files

.PHONY: params  help test test-all

help:
	@echo "DreamOCR API Examples"
	@echo ""
	@echo "Targets:"
	@echo "  test      Run OCR example"
	@echo ""
	@echo "Env vars: OCR_HOST, OCR_KEY"
params:
	@echo "OCR_HOST: $(OCR_HOST)"
	@echo "OCR_KEY: $(OCR_KEY)"


test:
	@echo "=== DreamOCR Test ==="
	$(PYTHON) ocr_example.py \
		-i $(TEST_DIR)/test_image.png \
		-o /tmp/aprbot_test_ocr/ \
		--host $(OCR_HOST) \
		-k $(OCR_KEY)

test-all: test
