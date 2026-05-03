---
phase: 06.1-ci-cd-pipeline-update-auth-service-deployment-postgres-independence-and-github-actions-build
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - .github/workflows/cd.yml
  - backend/pom.xml
  - auth-service/pom.xml
autonomous: true
requirements: []
must_haves:
  truths:
    - "Auth-service Docker image builds and pushes to GHCR on every master push"
    - "Version tag includes auth-service (all three services tagged with same semantic version)"
    - "CI job runs before deploy jobs and succeeds or fails atomically"
  artifacts:
    - path: ".github/workflows/cd.yml"
      provides: "Build and push job for auth-service Docker image"
      exports: ["Build step for auth-service", "Version tagging logic", "GHCR push"]
  key_links:
    - from: ".github/workflows/cd.yml (build-and-push)"
      to: "ghcr.io/goaldone-dhbw/goaldone-auth-service:VERSION"
      via: "docker/build-push-action"
      pattern: "Build step with auth-service Dockerfile"
    - from: ".github/workflows/cd.yml (version step)"
      to: "auth-service/pom.xml"
      via: "sed replacement of version tag"
      pattern: "Update <version> tag in auth-service pom.xml"
---

<objective>
Add auth-service Docker image build job to GitHub Actions CI/CD pipeline and integrate version tagging for all three services (backend, frontend, auth-service).

Purpose: Ensure auth-service is built, versioned, and pushed to GHCR alongside backend and frontend on every master push, following the existing versioning and deployment patterns.

Output: Updated `.github/workflows/cd.yml` with auth-service build job and unified version tagging for all three services.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@.planning/phases/06.1-ci-cd-pipeline-update-auth-service-deployment-postgres-independence-and-github-actions-build/06.1-CONTEXT.md
</execution_context>

<context>
@.planning/ROADMAP.md
@.planning/STATE.md
@.github/workflows/cd.yml — Current CD pipeline (backend and frontend builds)
@backend/pom.xml — Backend version file (pattern to replicate for auth-service)
</context>

<tasks>

<task type="auto">
  <name>Task 1: Update cd.yml — Add Auth-Service Build Job</name>
  <files>.github/workflows/cd.yml</files>
  <action>
    Extend the "Build and push" job to include a dedicated auth-service Docker image build step.
    
    **Current state:** Job builds and pushes backend and frontend images only.
    
    **Changes:**
    1. Add a new build-push step for auth-service (after frontend step, before deploy jobs):
       - Build context: `.` (root)
       - Dockerfile: `./auth-service/Dockerfile`
       - Tags: `ghcr.io/goaldone-dhbw/goaldone-auth-service:${{ steps.version.outputs.version }}` and `:latest`
       - Push to GHCR (same login as backend/frontend)
    
    2. Ensure step runs unconditionally (no path-based conditionals per D-13: Always Build Auth-Service).
    
    3. Execution order: Backend → Frontend → Auth-Service (auth-service last, doesn't block anything).
    
    **Reference:** Use existing docker/build-push-action v5 pattern from backend/frontend steps. Maintain consistent indentation and YAML structure.
    
    **Validation:** All three image builds complete successfully with matching version tag. No image push failures.
  </action>
  <verify>
    <automated>
      Verify .github/workflows/cd.yml contains auth-service build step:
      grep -A 10 'Build and push auth-service' .github/workflows/cd.yml | grep -q 'auth-service/Dockerfile' && echo "PASS: auth-service build step exists"
    </automated>
  </verify>
  <done>
    - Auth-service build step added to "Build and push" job
    - Tags point to ghcr.io/goaldone-dhbw/goaldone-auth-service:VERSION and :latest
    - Step uses docker/build-push-action v5 with push: true
    - Step executes after backend and frontend builds
  </done>
</task>

<task type="auto">
  <name>Task 2: Update cd.yml — Extend Version Update Logic for Auth-Service</name>
  <files>.github/workflows/cd.yml</files>
  <action>
    Extend the "Update version files" step to include auth-service version updates, following the same pattern as backend/frontend.
    
    **Current state:** Step updates backend/pom.xml and frontend/package.json only.
    
    **Changes:**
    1. Add sed command to update auth-service/pom.xml version tag:
       ```bash
       sed -i "s/<version>[0-9]*\.[0-9]*\.[0-9]*-SNAPSHOT<\/version>/<version>${NEW_VER}-SNAPSHOT<\/version>/" auth-service/pom.xml
       ```
    
    2. Placement: After frontend/package.json update, before git add.
    
    3. Assumption: auth-service pom.xml uses the same versioning pattern as backend (e.g., `<version>0.0.1-SNAPSHOT</version>`). If auth-service uses a different version format (e.g., properties), adjust the sed command accordingly (but this is implementation detail of Phase 1, assume Maven standard).
    
    **Reference:** Line 105-109 of existing cd.yml shows the backend/frontend pattern. Replicate for auth-service.
    
    **Validation:** All three version files updated correctly. Git commit includes all three.
  </action>
  <verify>
    <automated>
      Verify .github/workflows/cd.yml "Update version files" step includes auth-service sed command:
      grep -A 5 'Update version files' .github/workflows/cd.yml | grep -q 'auth-service/pom.xml' && echo "PASS: auth-service version update included"
    </automated>
  </verify>
  <done>
    - Auth-service/pom.xml version tag updated in the "Update version files" step
    - sed command matches the pattern used for backend pom.xml
    - Git add includes auth-service/pom.xml in the version commit
    - Commit message references all three services being versioned
  </done>
</task>

<task type="auto">
  <name>Task 3: Update cd.yml — Pass Auth-Service Version to Deploy Jobs</name>
  <files>.github/workflows/cd.yml</files>
  <action>
    Update both deploy-dev and deploy-prod jobs to receive and use the auth-service version (which equals the backend/frontend version per D-12: Version Tagging Unification).
    
    **Current state:** deploy.sh called with (stage, backend_version, frontend_version). Auth-service version not mentioned.
    
    **Changes:**
    1. Update deploy-dev and deploy-prod SSH scripts to add auth-service version parameter to deploy.sh call:
       - Current: `~/docker/scripts/deploy.sh dev $VERSION $VERSION`
       - New: `~/docker/scripts/deploy.sh dev $VERSION $VERSION $VERSION` (backend, frontend, auth-service)
    
    2. Add AUTH_SERVICE_* environment variables to envs list in SSH action:
       - Add: `DEV_AUTH_SERVICE_RSA_PRIVATE_KEY`, `DEV_AUTH_SERVICE_DB_PASSWORD`, `DEV_AUTH_SERVICE_ISSUER_URI` (and prod equivalents)
    
    3. Update the sed commands to populate AUTH_SERVICE_* variables in .env (before deploy.sh call):
       ```bash
       if [ -n "$DEV_AUTH_SERVICE_RSA_PRIVATE_KEY" ]; then
         sed -i 's/^AUTH_SERVICE_RSA_PRIVATE_KEY=.*/AUTH_SERVICE_RSA_PRIVATE_KEY='"$DEV_AUTH_SERVICE_RSA_PRIVATE_KEY"'/' ~/docker/dev/.env
       fi
       ```
       And similar for DB password and issuer URI.
    
    **Reference:** Lines 189-225 (deploy-dev) and 227-293 (deploy-prod). Follow the existing pattern for Zitadel variables.
    
    **Validation:** deploy.sh receives all three versions and all environment variables. No missing parameters.
  </action>
  <verify>
    <automated>
      Verify deploy-dev and deploy-prod pass auth-service parameters:
      grep 'deploy.sh dev.*VERSION.*VERSION' .github/workflows/cd.yml | grep -q '\$VERSION \$VERSION \$VERSION' && echo "PASS: auth-service version passed to deploy.sh"
    </automated>
  </verify>
  <done>
    - deploy-dev SSH script passes $VERSION as third argument (auth-service version)
    - deploy-prod SSH script passes $VERSION as third argument (auth-service version)
    - envs list includes DEV_AUTH_SERVICE_RSA_PRIVATE_KEY, DEV_AUTH_SERVICE_DB_PASSWORD, DEV_AUTH_SERVICE_ISSUER_URI
    - envs list includes corresponding PROD_* variants
    - .env sed commands populate all AUTH_SERVICE_* variables before deploy.sh runs
  </done>
</task>

</tasks>

<verification>
**After Plan 01 completes:**
- [ ] .github/workflows/cd.yml contains auth-service Docker build step (docker/build-push-action with auth-service/Dockerfile)
- [ ] .github/workflows/cd.yml "Update version files" step includes sed for auth-service/pom.xml
- [ ] deploy-dev and deploy-prod SSH scripts pass $VERSION three times to deploy.sh
- [ ] deploy-dev and deploy-prod include DEV/PROD_AUTH_SERVICE_* environment variables in envs list
- [ ] No merge conflicts or YAML syntax errors in cd.yml (can test with GitHub's workflow validator)
- [ ] Ready for Wave 2: Docker and Docker-Compose updates
</verification>

<success_criteria>
GitHub Actions CD pipeline includes auth-service Docker image build and version tagging alongside backend/frontend. Deploy jobs receive auth-service version and credentials from GitHub Secrets. All three services deployed with unified semantic version tag (e.g., v0.2.0).
</success_criteria>

<output>
After completion, create `.planning/phases/06.1-ci-cd-pipeline-update-auth-service-deployment-postgres-independence-and-github-actions-build/06.1-01-SUMMARY.md`
</output>
