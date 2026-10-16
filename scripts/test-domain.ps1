# =============================================================================
# Standalone Domain Test Runner for ScaleLink Core Engine
# Compiles and executes the Snowflake ID Generator and Base62 Encoder algorithms
# directly using Java 21 without requiring external build tools.
# =============================================================================

$ErrorActionPreference = "Stop"
$ProjectRoot = "C:\Users\Abha\Projects\scalelink"
$SrcDir = "$ProjectRoot\services\url-service\src\main\java"
$OutDir = "$ProjectRoot\services\url-service\target\classes"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " ScaleLink Domain Verification & Benchmark Suite (Java 21)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 1. Create target output directory
if (-not (Test-Path $OutDir)) {
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
}

# 2. Compile Domain Services & Exceptions
Write-Host "`n[1/3] Compiling pure domain models and algorithms with javac 21..." -ForegroundColor Yellow
$Sources = @(
    "$SrcDir\com\scalelink\urlservice\domain\exception\InvalidUrlException.java",
    "$SrcDir\com\scalelink\urlservice\domain\exception\UrlNotFoundException.java",
    "$SrcDir\com\scalelink\urlservice\domain\exception\UrlExpiredException.java",
    "$SrcDir\com\scalelink\urlservice\domain\exception\RateLimitExceededException.java",
    "$SrcDir\com\scalelink\urlservice\domain\exception\AliasAlreadyExistsException.java",
    "$SrcDir\com\scalelink\urlservice\domain\model\ShortUrl.java",
    "$SrcDir\com\scalelink\urlservice\domain\model\ClickEvent.java",
    "$SrcDir\com\scalelink\urlservice\domain\service\Base62Encoder.java",
    "$SrcDir\com\scalelink\urlservice\domain\service\SnowflakeIdGenerator.java"
)

javac -d $OutDir $Sources

Write-Host " Compilation successful!" -ForegroundColor Green

# 3. Create and compile test runner
$TestRunnerSrc = @"
package com.scalelink.urlservice;

import com.scalelink.urlservice.domain.service.Base62Encoder;
import com.scalelink.urlservice.domain.service.SnowflakeIdGenerator;
import com.scalelink.urlservice.domain.model.ShortUrl;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public class DomainVerificationRunner {
    public static void main(String[] args) {
        System.out.println("\n--- Running Domain Algorithms & Concurrency Test ---");

        // Test 1: Base62 Bidirectional Conversion
        Base62Encoder encoder = new Base62Encoder();
        long testNumber = 142839281948291072L;
        String encoded = encoder.encode(testNumber);
        long decoded = encoder.decode(encoded);

        System.out.printf("Test 1: Base62 Encoding for %d -> '%s'%n", testNumber, encoded);
        if (decoded != testNumber) {
            throw new AssertionError("Base62 decode mismatch! Expected " + testNumber + " but got " + decoded);
        }
        System.out.println(" Base62 encode/decode bidirectional check PASSED!");

        // Test 2: Snowflake ID Generator Collision Resistance (100,000 IDs)
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(42L);
        Set<Long> generatedIds = new HashSet<>();
        int testCount = 100_000;
        long startTime = System.nanoTime();

        for (int i = 0; i < testCount; i++) {
            long id = generator.nextId();
            if (id <= 0) {
                throw new AssertionError("Snowflake ID must be positive: " + id);
            }
            if (!generatedIds.add(id)) {
                throw new AssertionError("Collision detected at iteration " + i + " for ID: " + id);
            }
        }
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        double throughput = (testCount / (double) durationMs) * 1000;

        System.out.printf("Test 2: Generated %d Snowflake IDs with 0 collisions in %d ms (%.0f IDs/sec)%n",
                testCount, durationMs, throughput);
        System.out.println(" Snowflake collision-free uniqueness check PASSED!");

        // Test 3: ShortUrl Domain Invariants & Validation
        ShortUrl url = new ShortUrl(testNumber, encoded, "https://google.com/search?q=system-design", Instant.now(), null, false);
        if (url.isExpired()) {
            throw new AssertionError("Active URL incorrectly reported as expired!");
        }
        System.out.println(" ShortUrl domain entity invariants check PASSED!");

        System.out.println("\n==========================================================");
        System.out.println(" ALL DOMAIN VERIFICATION TESTS PASSED SUCCESSFULLY! (100%)");
        System.out.println("==========================================================\n");
    }
}
"@

$RunnerFile = "$ProjectRoot\services\url-service\src\test\java\com\scalelink\urlservice\DomainVerificationRunner.java"
Set-Content -Path $RunnerFile -Value $TestRunnerSrc

Write-Host "`n[2/3] Compiling DomainVerificationRunner..." -ForegroundColor Yellow
javac -cp $OutDir -d $OutDir $RunnerFile
Write-Host " Runner compiled successfully!" -ForegroundColor Green

# 4. Execute the test runner with java 21
Write-Host "`n[3/3] Executing verification test runner on JVM 21..." -ForegroundColor Yellow
java -cp $OutDir com.scalelink.urlservice.DomainVerificationRunner
