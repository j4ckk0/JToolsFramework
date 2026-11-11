# Publishing JToolsFramework: its licence, Maven Central

The framework is published under the Apache License, Version 2.0 (`LICENSE`, `NOTICE`), on GitHub
(<https://github.com/j4ckk0/JToolsFramework>) and on Maven Central under `org.jtools.framework`.

## 1. What reaches Maven Central

Every artifact with its pom, the version written out in each (the framework's poms have no `${revision}`, so no
`flatten-maven-plugin`):

| Artifact | What it is | Files |
|---|---|---|
| `org.jtools.framework:jtools-parent` | the build conventions every project inherits, the coordinates, the licence | the pom |
| `org.jtools.framework:jtools-bom` | the versions of the framework's artifacts, for a build not inheriting jtools-parent | the pom |
| `org.jtools.framework:jtools-support` | `Json`, `Catalogue`, `Texts`, `Installation` | jar, sources, javadoc |
| `org.jtools.framework:jtools-server` | `WebServer`, `Exchange`, `Answer`, `PageWatch`, `Http` | jar, sources, javadoc |
| `org.jtools.framework:jtools-app` | `WebApp`, `UserSettings`, `SettingsFields`, `LaunchOptions`, `LogFormatter` | jar, sources, javadoc |
| `org.jtools.framework:jtools-desktop` | `FileDialogs`: the native file dialogs | jar, sources, javadoc |
| `org.jtools.framework:jtools-dist` | the distributions' build (jlink runtimes, archives) and launchers | jar, sources; its javadoc jar is a README |
| `org.jtools.framework:jtools-web` | `@jtools/web`, as TypeScript sources | jar, sources; its javadoc jar is a README |
| `org.jtools.framework:jtools-devkit` | a project's scripts and the starter, which `scripts/jtools` fetches | jar, sources; its javadoc jar is a README |

`jtools-dist`, `jtools-web` and `jtools-devkit` hold no Java, and Maven Central refuses a jar without its javadoc: theirs is one
`README.txt` saying where their documentation is (`jtools.javadoc.placeholder.skip` in their poms). The aggregator
`jtools-framework`, which only lists the modules, is not uploaded (`excludeArtifacts`); nor is `examples/hello`
(`skipPublishing`).

**This is not part of a release**: a version reaches Central only when it is asked for on its own — what Central
publishes can never be replaced or withdrawn.

## 2. What it takes, once

Shared by every `org.jtools` artifact, and done already:

1. **The namespace `org.jtools`.** Maven Central hands out a groupId only to who owns the domain it reverses, and
   jtools.org is ours: verified on <https://central.sonatype.com> through a DNS TXT record on `jtools.org`.
   `org.jtools.framework` is under it: nothing to verify again.
2. **The portal's user token** (*View Account* ▸ *Generate User Token*), kept as the server `central` in
   `~/.m2/settings.xml` — there already:

       <settings><servers><server>
         <id>central</id><username>…</username><password>…</password>
       </server></servers></settings>

3. **A GPG key**, its public part on a key server the portal reads — there already. `maven-gpg-plugin` signs with
   the default key, or `-Dgpg.keyname=<id>`. Central refuses an unsigned bundle.

## 3. Publishing a version

The profile `publish` is jtools-parent's (doc/CONVENTIONS.md); jtools-bom, which inherits nothing, has its own: the framework's modules inherit it, and so can a published
project of its own.

1. The version, in every pom at once (`scripts/check-versions.sh` says they agree), not a SNAPSHOT; the projects'
   template follows it (`starter/template/pom.xml` names `__JTOOLS_VERSION__`, filled in from `jtools.version`), and
   `scripts/jtools` names it as `JTOOLS_DEFAULT_VERSION`.
2. A dry run, nothing signed nor sent — the jars, sources and javadoc in each module's `target/`:

       . scripts/externals.sh && mvn -Ppublish clean verify -Dgpg.skip

3. The upload, from the root: every module's tests, the jars, their sources and javadoc, signed, one bundle sent to
   the portal, where it is validated:

       . scripts/externals.sh && mvn -Ppublish clean deploy

4. Nothing is released until **Publish** is pressed on <https://central.sonatype.com> (`autoPublish` is off), so a
   mistake costs nothing: drop the deployment there, fix, upload again.
5. Then tag the version (`git tag v1.2.0 && git push origin v1.2.0`) — doc/GETTING-STARTED.md downloads
   `scripts/jtools` from that tag — and, in the projects, name the new version as their parent's and as
   `JTOOLS_VERSION` (`scripts/jtools sync` reports the ones behind).
6. The delivery: `scripts/delivery.sh` builds `target/delivery/jtools-<version>.tar.gz` and its `.sha256` from what the
   tag holds (it refuses when `libraries/`, `conventions/`, `devkit/` or `starter/` differ from it); attach both to the
   tag's release on GitHub (*Releases* ▸ *Draft a new release* ▸ the tag), which README.md and GETTING-STARTED.md
   download it from.

## 4. Learnt the first time

- `gpg --send-keys` reports success here while the key never leaves: the key-server ports are filtered. Upload over
  HTTPS instead — `gpg --armor --export <id> > pub.asc` then
  `curl --data-urlencode "keytext@pub.asc" https://keyserver.ubuntu.com/pks/add`, which answers with the
  fingerprint it inserted.
- A failed build after `Uploaded bundle successfully` does not mean a failed upload: the plugin polls the portal
  afterwards, and an old one throws on a field the portal has since added (hence 0.11.0 at least). Ask the portal
  itself rather than trusting the exit code:

      curl -X POST -H "Authorization: Bearer $(printf '%s:%s' "$USER_TOKEN" "$PASSWORD" | base64 -w0)" \
        "https://central.sonatype.com/api/v1/publisher/status?id=<deploymentId>"

  `deploymentState: VALIDATED` means it is staged and waiting for **Publish**.

These builds use the network (plugins, the upload): the framework does not.

## 5. Once on Central

The projects no longer need `scripts/install.sh` first: Maven resolves jtools-parent and the libraries from Central,
`scripts/jtools` the devkit; a hosted CI builds a project with nothing but Maven.
