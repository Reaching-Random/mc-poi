# Security Policy

## Supported versions

Security fixes are released for the newest build on every supported Minecraft version: the default branch and each `LTS/*` branch.

## Reporting a vulnerability

Please **don't** open a public issue. Report it privately through GitHub instead:
[Report a vulnerability](https://github.com/Reaching-Random/mc-poi/security/advisories/new) (the **Security** tab → **Report a vulnerability**).

Include what you found, how to reproduce it, and the versions affected. You'll get a reply as soon as possible, and you'll be credited in the advisory unless you'd rather not be.

Problems with the Reaching Random website or its API (rather than this mod) can be reported the same way.

## Your API key

The mod stores your Reaching Random API key in plain text in `config/poi/<your-uuid>-profile.json` and only sends it over HTTPS. Don't share that file, and run `/poi reset` (then create a new key on the website) if you think it has leaked.
