#!/bin/bash
# What "done" means for this package, in one command.
#
# ═══ WHY THIS SCRIPT EXISTS ═══
#
# `swift test` on a Mac compiles neither MonetizationBrainUMP nor anything else guarded by
# `#if canImport(UIKit)`, because UIKit is not there. That is not a small gap: the UMP target was
# left UNCOMPILABLE FOR IOS — its only platform — for several phases, by a duplicate declaration
# that macOS never saw and that no test could catch, since that target had no test target either.
# It has one now (see the simulator step below), but that does not retire this step: a test target
# proves the code it calls, and an iOS build proves the code nothing calls.
#
# So the definition of done is not "tests pass". It is: every target builds for iOS, and the tests
# pass. Run this before calling anything finished.
#
# ═══ AND WARNINGS COUNT AS FAILURES ═══
#
# This script used to say "warnings are errors here by convention, check the log" and then never
# check. It only grepped the log when a step EXITED non-zero, and a warning does not do that — so
# the convention was enforced by nobody. That is the same blind spot as above, one level up: the
# iOS-only UMP target carried a main-actor isolation warning (a HARD ERROR under the Swift 6
# language mode) across several phases, visible in no macOS build and surfaced by no green run of
# this script. Warnings from this package's own Sources now fail the step.
#
# ═══ AND THE UMP TESTS ONLY EXIST ON A SIMULATOR ═══
#
# `MonetizationBrainUMPTests` covers the ConsentStatus -> ConsentTier table, the one decision that
# target makes. `UserMessagingPlatform` is iOS-only, so under `swift test` that file compiles to
# NOTHING and reports a cheerful zero failures. It is run below on an iOS Simulator, where it means
# something. Same blind spot as the other two, for the third time: if it only builds for iOS, a
# macOS run tells you nothing about it.
#
# `StoreKitHostedTests` runs there too, out of the Xcode project rather than the package, because
# StoreKit testing needs a host app. `xcodebuild` calls an all-skipped target a success, which is
# a worse lie than a failure, so that step fails on any skip.
set -uo pipefail
cd "$(dirname "$0")/.."

failed=0
# Per-step ceiling. Generous enough for a cold Firebase resolve on a CI runner, short enough that
# a hung simulator fails the run instead of eating the whole job budget.
STEP_TIMEOUT=900

# A per-step timeout, written out by hand rather than shelling out to `timeout`.
#
# `timeout(1)` is GNU coreutils and macOS DOES NOT SHIP IT. Using it here passed locally, on a Mac
# that happens to have coreutils installed, and failed EVERY step on the CI runner in five seconds
# flat — "command not found" for each one, which the failure branch then reported as an empty
# FAILED with nothing under it. The reason a timeout exists at all is that a StoreKit purchase
# whose confirmation dialog was not disabled blocks forever, and one CI run sat on that for 27
# minutes before the job timeout killed it with no output.
#
# So: background the command, poll for it, and kill it if the deadline passes. Returns 124 on
# timeout the way `timeout` does, and the command's own status otherwise.
with_timeout() {
    local secs=$1
    shift
    "$@" > /tmp/verify_step.log 2>&1 &
    local pid=$!
    local waited=0
    while kill -0 "$pid" 2>/dev/null; do
        if [ "$waited" -ge "$secs" ]; then
            kill -9 "$pid" 2>/dev/null
            wait "$pid" 2>/dev/null
            return 124
        fi
        sleep 1
        waited=$((waited + 1))
    done
    wait "$pid"
}
# Warnings are scoped to this package's own Sources/ on purpose: a future Firebase or UMP release
# is free to warn in code we do not own, and failing the build on that would make this script
# useless exactly when a dependency bump needs verifying.
run() {
    echo "── $1"
    shift
    with_timeout "$STEP_TIMEOUT" "$@"
    local rc=$?
    if [ "$rc" -eq 124 ]; then
        echo "   FAILED: timed out after ${STEP_TIMEOUT}s"
        failed=1
        return
    fi
    if [ "$rc" -ne 0 ]; then
        echo "   FAILED:"
        # A failing xctest prints neither "error:" nor "BUILD FAILED". Both spellings are matched
        # here: `-quiet` reduces a failure to a "Failing tests:" block, while a verbose run gives
        # the actual XCTAssert/XCTUnwrap line. Without these this branch printed the word FAILED
        # and not one line of why.
        grep -E "error:|BUILD FAILED|Failing tests:|XCTAssert[A-Za-z]* failed|XCTUnwrap failed" \
            /tmp/verify_step.log | head -12 | sed "s|$PWD/||" | sed 's/^/     /'
        failed=1
        return
    fi
    local warnings
    warnings=$(grep -E "warning:" /tmp/verify_step.log | grep -F "$PWD/Sources/" | sort -u)
    if [ -n "$warnings" ]; then
        echo "   FAILED (warnings are errors here):"
        echo "$warnings" | head -10 | sed "s|$PWD/||" | sed 's/^/     /'
        failed=1
        return
    fi
    # `xcodebuild` exits 0 when every test in a target SKIPPED, which is the most dangerous shape
    # of green there is — indistinguishable from a run that verified something. For steps marked
    # NO_SKIPS, that is a failure, and it is decided HERE so the step prints one verdict instead
    # of "ok" followed by a contradiction.
    if [ "${NO_SKIPS:-0}" = "1" ]; then
        local skipped
        skipped=$(grep -oE "[0-9]+ tests skipped" /tmp/verify_step.log | head -1)
        if [ -n "$skipped" ]; then
            echo "   FAILED: $skipped, and this step must never skip —"
            echo "     it exists precisely so these run. A skip means StoreKit testing broke,"
            echo "     not that the tests became unnecessary."
            grep -oE "Test skipped - .*" /tmp/verify_step.log | head -1 | cut -c1-200 \
                | sed 's/^/     /'
            failed=1
            return
        fi
    fi
    # Print the test counts on SUCCESS, not just on failure.
    #
    # Everything below the top of this file is about green runs that verified nothing. A step that
    # prints only "ok" is one of them from the reader's side: the CI log for a passing run had no
    # record of whether 16 tests ran or 0 did, so "ok" had to be taken on trust. The counts make a
    # pass auditable after the fact, which is the whole point of keeping the log.
    #
    # Take the LARGEST count, not the last one. xctest prints an "Executed N" line per suite and
    # then one for "All tests"; taking the last printed line reported `swift test` as 9 tests when
    # it had run 833 — a number that is worse than no number, because it looks like a measurement.
    # The total is by definition the maximum, whatever order the suites finished in.
    local executed
    executed=$(grep -oE "Executed [0-9]+ tests?, with ([0-9]+ tests? skipped and )?[0-9]+ failures? \([0-9]+ unexpected\)" \
        /tmp/verify_step.log | sort -u \
        | sed -E 's/^Executed ([0-9]+)/\1 &/' | sort -rn | head -1 | cut -d' ' -f2-)
    if [ -n "$executed" ]; then
        echo "   ok — $executed"
    else
        echo "   ok"
    fi
}

run "macOS build" \
    swift build
run "tests" \
    swift test

for scheme in MonetizationBrainCore MonetizationBrainUMP MonetizationBrainStoreKit MonetizationBrainFirebase; do
    run "iOS build: $scheme" \
        xcodebuild -scheme "$scheme" -destination 'generic/platform=iOS' -quiet build
done

# Pick whatever iPhone this machine actually has rather than pinning a model that a future Xcode
# drops. A missing simulator must FAIL, not silently skip: a skipped test step that prints nothing
# is how the UMP target went unverified in the first place.
simulator=$(xcrun simctl list devices available | grep -oE 'iPhone [^(]*' | head -1 | sed 's/ *$//')
if [ -z "$simulator" ]; then
    echo "── iOS Simulator tests"
    echo "   FAILED: no available iPhone simulator; MonetizationBrainUMPTests cannot run"
    failed=1
else
    # NO `-quiet` here, unlike the build steps. It suppresses both the skip summary and the
    # failing assertions, which are the only two things this step exists to tell you. Output goes
    # to the log either way and is only printed on failure, so the verbosity costs nothing.
    run "iOS Simulator tests ($simulator): MonetizationBrainUMPTests" \
        xcodebuild test -scheme MonetizationBrainIOS-Package \
            -destination "platform=iOS Simulator,name=$simulator" \
            -only-testing:MonetizationBrainUMPTests

    # ═══ SKIPS ARE REPORTED, NOT SWALLOWED ═══
    #
    # `xcodebuild test` prints ** TEST SUCCEEDED ** when every test in a target SKIPPED, which is
    # the most dangerous shape of green there is: it looks identical to a run that verified
    # something. MonetizationBrainStoreKitIntegrationTests currently skips ALL of its tests,
    # because a SwiftPM test target has no host app and StoreKit refuses an unentitled process —
    # see that file's type doc. Print the count and the first reason so a reader of this output
    # knows what did NOT run. Not a failure: the skip is a known, documented limitation, and
    # failing on it would only train people to ignore the step.
    # StoreKit2Backend against a real StoreKit, out of the Xcode project. See that test file's
    # doc for the three things StoreKit needs (host app, ad-hoc signing, get-task-allow) and why
    # none of them fit in Package.swift. This is the ONLY step that exercises that adapter for
    # real; without it the file has just the mapping tests' 24%.
    NO_SKIPS=1 run "iOS Simulator tests ($simulator): StoreKitHostedTests" \
        xcodebuild test \
            -project Integration/MonetizationBrainIntegration.xcodeproj \
            -scheme StoreKitHostedTests \
            -destination "platform=iOS Simulator,name=$simulator"
fi

if [ "$failed" -eq 0 ]; then
    echo "── all green"
else
    echo "── SOMETHING FAILED, see above"
fi
exit "$failed"
