# DreamDocs Java API Examples

# Load local env vars if .env exists
ifneq (,$(wildcard .env))
    include .env
    export
endif


JAVA_ROOT  := ../..
TEST_DIR   := ../test_files

CP := target/classes:$(shell cd $(JAVA_ROOT) && mvn dependency:build-classpath -q -DincludeScope=compile -Dmdep.outputFile=/dev/stdout 2>/dev/null)

.PHONY: params  help compile test-predict test-search test-setup test-ocr test-all

help:
	@echo "DreamDocs Java API Examples"
	@echo ""
	@echo "Targets:"
	@echo "  compile     Compile Java sources"
	@echo "  test-predict  Upload and predict fields"
	@echo "  test-search   Search documents"
	@echo "  test-setup    List docsets and doctypes"
	@echo "  test-ocr      Run OCR via DreamDocs"
	@echo "  test-all      Run all examples"
	@echo ""
	@echo "Env vars: DOCS_HOST, DOCS_USER, DOCS_PASS"
params:
	@echo "DOCS_HOST: $(DOCS_HOST)"
	@echo "DOCS_USER: $(DOCS_USER)"
	@echo "DOCS_PASS: $(DOCS_PASS)"


compile:
	cd $(JAVA_ROOT) && mvn compile -q

test-predict: compile
	@echo "=== DreamDocs Java Predict Test ==="
	cd $(JAVA_ROOT) && java -cp "$(CP)" PredictExample \
		-i $(TEST_DIR)/test_document.pdf \
		-o /tmp/aprbot_test_docs_java/ \
		--host $(DOCS_HOST) \
		-u $(DOCS_USER) \
		-p $(DOCS_PASS) \
		--project-id 1

test-search: compile
	@echo "=== DreamDocs Java Search Test ==="
	cd $(JAVA_ROOT) && java -cp "$(CP)" SearchExample \
		--host $(DOCS_HOST) \
		-u $(DOCS_USER) \
		-p $(DOCS_PASS)

test-setup: compile
	@echo "=== DreamDocs Java Setup Test ==="
	cd $(JAVA_ROOT) && java -cp "$(CP)" SetupExample \
		--host $(DOCS_HOST) \
		-u $(DOCS_USER) \
		-p $(DOCS_PASS)

test-ocr: compile
	@echo "=== DreamDocs Java OCR Test ==="
	cd $(JAVA_ROOT) && java -cp "$(CP)" DocsOcrExample \
		-i $(TEST_DIR)/test_image.png \
		-o /tmp/aprbot_test_docs_ocr_java/ \
		--host $(DOCS_HOST) \
		-u $(DOCS_USER) \
		-p $(DOCS_PASS) \
		--project-id 1

test-all: test-predict test-search test-setup test-ocr
