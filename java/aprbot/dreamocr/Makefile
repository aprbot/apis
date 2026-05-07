# DreamOCR Java API Examples

# Load local env vars if .env exists
ifneq (,$(wildcard .env))
    include .env
    export
endif


JAVA_ROOT  := ../..
TEST_DIR   := ../test_files

CP := target/classes:$(shell cd $(JAVA_ROOT) && mvn dependency:build-classpath -q -DincludeScope=compile -Dmdep.outputFile=/dev/stdout 2>/dev/null)

.PHONY: params  help compile test test-all

help:
	@echo "DreamOCR Java API Examples"
	@echo ""
	@echo "Targets:"
	@echo "  compile   Compile Java sources"
	@echo "  test      Run OCR example"
	@echo "  test-all  Run all examples"
	@echo ""
	@echo "Env vars: OCR_HOST, OCR_KEY"
params:
	@echo "OCR_HOST: $(OCR_HOST)"
	@echo "OCR_KEY: $(OCR_KEY)"


compile:
	cd $(JAVA_ROOT) && mvn compile -q

test: compile
	@echo "=== DreamOCR Java Test ==="
	cd $(JAVA_ROOT) && java -cp "$(CP)" OcrExample \
		-i $(TEST_DIR)/test_image.png \
		-o /tmp/aprbot_test_ocr_java/ \
		-k $(OCR_KEY) \
		--host $(OCR_HOST)

test-all: test
