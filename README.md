# TAKT BPMN

## General Purpose

The TAKT BPMN project is designed to facilitate the integration and execution of BPMN (Business Process Model and Notation) workflows 
within a Java-based application. It leverages the Quarkus framework for building efficient and scalable applications and provides a 
client for interacting with external task instances.

## Modules

### `testclient-quarkus`

This module contains the implementation of the BPMN workflows and the workers that handle the tasks defined in the BPMN processes. It includes:

- **Workers**: Classes annotated with `@TaktWorker` and `@TaktWorkerMethod` to define the tasks and their execution logic.
- **BPMN Resources**: BPMN files that define the workflows and processes.
- **Configuration**: Setup and configuration for the Quarkus application to run the BPMN workflows.
