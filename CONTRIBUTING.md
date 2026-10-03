# Contributing to Points of Interest

Thanks for helping out. Bug reports, ideas and pull requests are all welcome.

## Before you start

- **Bugs:** open an issue with the bug report form. Include your Minecraft, mod and Fabric API versions, and the relevant part of `logs/latest.log`.
- **Features:** open an issue to discuss the idea before writing code, so you don't spend time on something that won't be merged.
- **Security problems:** don't open a public issue. See [SECURITY.md](SECURITY.md).

## Branches

Each supported Minecraft version has its own branch, because the Minecraft and Fabric APIs change between versions:

| Branch | Minecraft |
|---|---|
| default branch (named after the version, e.g. `26.3`) | newest supported version |
| `LTS/<version>` (e.g. `LTS/26.2`, `LTS/1.21.11`) | older supported versions |

**Open pull requests against the default branch.** The maintainer ports accepted changes to the `LTS/*` branches. If a bug only affects an older version, target that `LTS/<version>` branch instead and say so in the PR.

## Building

You need JDK 25 for the 26.x branches (JDK 21 or newer for `LTS/1.21.11`).

```bash
./gradlew build       # build the jar into build/libs/
./gradlew runClient   # launch a development client with the mod
```

There are no automated tests yet, so describe in the PR how you checked the change in-game.

## Pull requests

- Keep each PR to one change. Separate refactors from behavior changes.
- Use [Conventional Commits](https://www.conventionalcommits.org/) subjects: `fix:`, `feat:`, `refactor:`, `chore:`, `build:`.
- Don't change `mod_version` or other version pins. Releases are versioned by the maintainer.
- Match the surrounding code style.

## License

By contributing, you agree that your contributions are licensed under the project's [MIT License](LICENSE).
