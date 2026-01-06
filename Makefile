SHELL := /bin/sh

MVN ?= mvn
JAVA ?= java

ARTIFACT_ID := simulateur_java_vehicles
VERSION := 1.0-SNAPSHOT
JAR := target/$(ARTIFACT_ID)-$(VERSION).jar
JAVA_OPTS ?= --enable-native-access=ALL-UNNAMED

.PHONY: help build run test clean package

help:
	@printf "Usage: make <target>\n\n"
	@printf "Targets:\n"
	@printf "  build   Compile and package the jar (skip tests)\n"
	@printf "  run     Build then run the simulator\n"
	@printf "  test    Run unit tests\n"
	@printf "  clean   Remove build artifacts\n"

build:
	@$(MVN) -q -DskipTests package

run: build
	@$(JAVA) $(JAVA_OPTS) -jar $(JAR)

test:
	@$(MVN) -q test

clean:
	@$(MVN) -q clean
