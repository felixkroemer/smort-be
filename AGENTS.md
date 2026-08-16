# AGENTS.md

## Git workflow
- Never start implementation on main/master. Always create a feature branch
  for any plan or task before writing code. Do NOT use git worktrees.
- Ignore the using-git-worktrees skill bundled with superpowers; do not invoke it.
- Commit all work to the feature branch; leave main untouched.
- When a task or plan is complete, push the feature branch to origin. Do not
  present the finishing-a-development-branch menu; pushing to origin is the
  expected completion flow.
- Remove implementation plans and spec documents (in docs/superpowers/) once their work is complete.

## Working preferences
- Write tests only when explicitly asked (in the plan, or by the human).
  Never create additional tests on your own initiative, including regression
  tests for new code.
- Do not volunteer refactors or fixes. Surface the issue or option and let the
  human decide whether to implement it. Implement only what the human green-lights.
- Specs and implementation plans are living documents, not contracts. When the
  human requests a deviation mid-execution, implement it directly and note the
  deviation in the report instead of treating it as a spec violation.

## Build environment
- Implementing and reviewing subagents must NOT attempt to run, fix, or
  debug the build (`./mvnw compile`, `./mvnw test`, etc.). The human owns
  compilation and verifies it later. Skip build/compile verification steps
  and note in reports that compilation was skipped per this instruction.
