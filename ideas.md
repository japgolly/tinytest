Check out other test libs from other languages

# Constraints
* Not use macros
* Not use too many implicits (eg. 1 per test method decl is too much)
* No nice assertion DSL, `fail` only
* concise

# Functional (def)
* Before/after each (in some scope)
* Before/after all (in some scope)
* Lazy global resources (init when needed, shutdown at end of ALL tests)
* Sole/ignore
* Dynamic/data-based tests (eg. ∀a∈[…:A]. name x test)
* Shared tests (test tree as data)
* Async tests
* Nested tests / groups
* Dynamically skip tests when `<runtime reason>` (eg. no internet → report "18 tests skipped because no internet")
* Support testing compilation errors

# Functional (run)
* Duration stats (and warnings configurable from SBT)
* Big failures/stacktraces/output (?) should be clearly associated with the test case, also non-interleaved if possible
* Aggregate multi-module test result summaries
* Self-updating progress/output, maybe something like:
  * a dot per test in a pending bucket which moves into an in-progress bucket and then to pass/fail/skip buckets
* Capture stdout/stderr during test run, then
  1. print it out all atomically
  2. print it out AFTER the test framework prints the test case and result
