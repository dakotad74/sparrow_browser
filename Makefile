# Sparrow Browser - Makefile
# Comandos útiles para desarrollo

.PHONY: help run test build clean jpackage all

# Colores para output
GREEN := \033[0;32m
YELLOW := \033[1;33m
NC := \033[0m # No Color

help: ## Mostrar esta ayuda
	@echo "Sparrow Browser - Comandos Disponibles:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(GREEN)%-15s$(NC) %s\n", $$1, $$2}'
	@echo ""

run: ## Ejecutar Sparrow (shortcut para ./sparrow)
	@./sparrow

test: ## Ejecutar tests de coordinación
	@echo "$(YELLOW)Ejecutando tests de coordinación...$(NC)"
	@./gradlew test --tests CoordinationIntegrationTest
	@./gradlew test --tests CoordinationWorkflowTest.testFeeProposalReplacement

test-all: ## Ejecutar TODOS los tests
	@echo "$(YELLOW)Ejecutando todos los tests...$(NC)"
	@./gradlew test

build: ## Compilar proyecto
	@echo "$(YELLOW)Compilando proyecto...$(NC)"
	@./gradlew build

jpackage: ## Crear binario jpackage
	@echo "$(YELLOW)Creando binario jpackage...$(NC)"
	@./gradlew jpackage

rebuild: clean jpackage ## Limpiar y recompilar todo
	@echo "$(GREEN)✅ Rebuild completo$(NC)"

clean: ## Limpiar archivos compilados
	@echo "$(YELLOW)Limpiando build...$(NC)"
	@./gradlew clean

quick: ## Compilar rápido y ejecutar (para desarrollo)
	@./gradlew compileJava
	@./gradlew jpackage
	@./sparrow --quiet

commit: ## Hacer commit con mensaje
	@read -p "Mensaje de commit: " msg; \
	git add .; \
	git commit -m "$$msg"; \
	echo "$(GREEN)✅ Commit realizado$(NC)"

push: ## Push a origin/master
	@git push origin master
	@echo "$(GREEN)✅ Push completado$(NC)"

status: ## Ver estado de git y proyecto
	@echo "$(YELLOW)=== Git Status ===$(NC)"
	@git status -sb
	@echo ""
	@echo "$(YELLOW)=== Tests Status ===$(NC)"
	@./gradlew test --tests CoordinationIntegrationTest 2>&1 | grep -E "(PASSED|FAILED|tests completed)" || echo "Tests no ejecutados aún"

all: clean build jpackage ## Compilar todo desde cero
	@echo "$(GREEN)✅ Build completo realizado$(NC)"
