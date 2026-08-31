# Setup, Run, and Logging Guide for `apis-main`

This document provides step-by-step instructions to clone, build, run, and test `apis-main` on Linux, macOS, and Windows. It also includes instructions for capturing logs and a template for reporting results.

## 1. Prerequisites

Before starting, ensure you have the following installed on your system.

### All Operating Systems
*   **Java JDK 8 or 11+** (Target is Java 1.8).
    *   Verify: `java -version`
*   **Maven 3.6+**.
    *   Verify: `mvn -version`
*   **Git**.
    *   Verify: `git --version`

---

## 2. Installation & Build Process

The `apis-main` project depends on `apis-bom` and `apis-common`, which must be built locally first.

**Commands (Run sequentially in your workspace directory):**

1.  **Install `apis-bom`**:
    ```bash
    git clone https://github.com/hyphae/apis-bom.git
    cd apis-bom
    mvn install
    cd ..
    ```

2.  **Install `apis-common`**:
    ```bash
    git clone https://github.com/hyphae/apis-common.git
    cd apis-common
    mvn install
    cd ..
    ```

3.  **Build `apis-main` (This Repository)**:
    ```bash
    # If not already cloned
    # git clone https://github.com/hyphae/apis-main.git
    cd apis-main
    mvn package
    ```

    **Capture Build Logs:**
    To save the build output to a file:
    ```bash
    mvn package > build.log 2>&1
    ```

---

## 3. Running the Application

The application is executed from the `exe` directory.

### Linux & macOS
1.  Navigate to the executable directory:
    ```bash
    cd exe
    ```
2.  Run the start script:
    ```bash
    bash start.sh
    ```
    *   **macOS Note**: The script automatically detects runs on macOS (Darwin) and uses `cluster-mac.xml`.
    *   **Capture Logs**: `bash start.sh > run.log 2>&1`

3.  Stop the application:
    ```bash
    bash stop.sh
    ```

### Windows
**Option 1: Git Bash (Recommended)**
Use the Linux instructions above within a Git Bash terminal.

**Option 2: PowerShell**
If you must use PowerShell, run the Java command manually:

1.  Navigate to the executable directory:
    ```powershell
    cd exe
    ```
2.  Run the application:
    ```powershell
    $jarPath = "../target/apis-main-3.0.0-fat.jar"
    java -Djava.net.preferIPv4Stack=true -Duser.timezone=Asia/Tokyo -Dlogback.configurationFile=./logback.xml -Dvertx.hazelcast.config=./cluster.xml -jar $jarPath -conf ./config.json -cluster -cluster-host 127.0.0.1
    ```
3.  Stop the application:
    Press `Ctrl+C` in the terminal window.

---

## 4. Testing

To run the unit tests:

```bash
mvn test
```

**Capture Test Logs:**
```bash
mvn test > test_results.log 2>&1
```

---

## 5. Logs & Troubleshooting

### Where to Find Logs
*   **Standard Output**: Displayed in the terminal during execution.
*   **Log Files**: Created in the `exe` directory (configured via `logback.xml`).
    *   `*.log`: General logs (e.g., `0.0.log`).
    *   `*.err`: Error logs.

### Common Errors

**Error: `mvn: command not found`**
*   **Cause**: Maven is not installed or not in your system PATH.
*   **Fix**: Install Maven and ensure `mvn` works in a new terminal.

**Error: Dependency not found (`jp.co.sony.csl.dcoes.apis:apis-bom:pom:3.0.0`)**
*   **Cause**: You skipped step 2 (Installation). `apis-bom` and `apis-common` are not on Maven Central; they must be installed locally.
*   **Fix**: Clone and run `mvn install` for both `apis-bom` and `apis-common`.

**Error: Port in use (BindException)**
*   **Cause**: Another instance of `apis-main` is running.
*   **Fix**: Run `bash stop.sh` or manually kill the Java process.

---

## 6. Report Template (For GitHub Issue)

Use the following template to report your results for each OS.

```markdown
# Local Setup & Test Report

## Summary
| OS      | Build Status | Run Status | Test Status | Notes |
|---------|--------------|------------|-------------|-------|
| Linux   | [Untested]   | [Untested] | [Untested]  |        |
| macOS   | [Untested]   | [Untested] | [Untested]  |        |
| Windows | **FAIL**     | **FAIL**   | **FAIL**    | Critical Dependency Conflict (Vert.x 3 vs 4) |

## 1. Windows Execution Report

**Date**: 2026-02-02
**Environment**: Windows, OpenJDK 21.0.8, Maven 3.9.6.

### Setup & Build
*   **Action**: Cloned `apis-bom`, `apis-common`, and `apis-main`. Built hierarchically.
*   **Observation**: `apis-bom` and `apis-common` built successfully (after ensuring versions aligned).
*   **Result**: `apis-main` compilation was successful.

### Test Execution
*   **Command**: `mvn test`
*   **Status**: **FAILURE**
*   **Error Log**:
    ```text
    jp.co.sony.csl.dcoes.apis.main.app.ApisTest.testDeployAndUnDeploy -- Time elapsed: 0.395 s <<< ERROR!
    java.lang.IllegalStateException: No ClusterManagerFactory instances found on classpath
        at io.vertx.core.impl.VertxImpl.getClusterManager(VertxImpl.java:470)
        at io.vertx.core.impl.VertxImpl.<init>(VertxImpl.java:172)
    ```
*   **Root Cause Analysis**:
    The `apis-main/pom.xml` specifies `vertx-core:3.7.1`.
    However, the current `apis-bom` (version 3.4.1) specifies `vertx-core:4.4.6`.
    `apis-common` (built against the BOM) uses Vert.x 4.x classes.
    When `apis-main` runs, it mixes Vert.x 3 (from its own POM) and Vert.x 4 (transitively from common/bom), causing the `ClusterManagerFactory` error because the SPI mechanism changed between versions.

### Run Execution
*   **Command**: `bash start.sh` (or equivalent java command)
*   **Status**: **FAILURE**
*   **Observation**: Application crashes at startup due to the same version mismatch/class loading errors.

### Recommendation for Maintainers
1.  **Update `apis-main` to use Vert.x 4.x** to match `apis-bom` and `apis-common`.
2.  Or, **Rollback `apis-bom`** to a version compatible with Vert.x 3.7.1 (likely v3.0.0 is needed, but was not found in tags).
```
