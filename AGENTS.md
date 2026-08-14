# AGENTS.md

## Git workflow
- Never start implementation on main/master. Always create a feature branch
  (or isolated git worktree) for any plan or task before writing code.
- Commit all work to the feature branch; leave main untouched.
- When a task or plan is complete, push the feature branch to origin.
- Remove implementation plans and spec documents (in docs/superpowers/) once their work is complete.

## Build environment
- Implementing and reviewing subagents must NOT attempt to run, fix, or
  debug the build (`./mvnw compile`, `./mvnw test`, etc.). The human owns
  compilation and verifies it later. Skip build/compile verification steps
  and note in reports that compilation was skipped per this instruction.
