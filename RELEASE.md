# Release Process

This project uses automated releases through GitHub Actions. Here's how to create a new release:

## Creating a Release

1. **Ensure your changes are merged to main**
   - All changes should be merged via pull requests
   - The main branch should be stable and ready for release

2. **Create and push a version tag**
   ```bash
   # Create a new version tag (use semantic versioning)
   git tag v1.0.0
   
   # Push the tag to GitHub
   git push origin v1.0.0
   ```

3. **Automated Release Process**
   The GitHub Actions workflow will automatically:
   - Build the JAR file using Gradle
   - Build and push a Docker image to GitHub Container Registry
   - Create a deployment-ready `compose-deployment.yaml` file
   - Generate a deployment README
   - Create a GitHub release with auto-generated release notes
   - Attach all artifacts to the release

## Release Artifacts

Each release includes:
- **JAR file**: The compiled Spring Boot application
- **compose-deployment.yaml**: Ready-to-use Docker Compose file
- **DEPLOYMENT.md**: Deployment instructions
- **compose.yaml**: Original development compose file
- **Docker Image**: Available at `ghcr.io/tadeasfort/pcpartsscraper:v1.0.0`

## Version Numbering

Use [Semantic Versioning](https://semver.org/):
- **MAJOR** version (v2.0.0): Incompatible API changes
- **MINOR** version (v1.1.0): New functionality (backwards compatible)
- **PATCH** version (v1.0.1): Bug fixes (backwards compatible)

## Examples

```bash
# First release
git tag v1.0.0
git push origin v1.0.0

# Feature release
git tag v1.1.0
git push origin v1.1.0

# Bug fix release
git tag v1.0.1
git push origin v1.0.1
```

## Deployment

Users can deploy the application using:

1. **Docker Compose (Recommended)**:
   ```bash
   wget https://github.com/tadeasfort/pcpartsscraper/releases/download/v1.0.0/compose-deployment.yaml
   docker compose -f compose-deployment.yaml up -d
   ```

2. **JAR file**:
   ```bash
   wget https://github.com/tadeasfort/pcpartsscraper/releases/download/v1.0.0/pcpartsscraper-0.0.1-SNAPSHOT.jar
   java -jar pcpartsscraper-0.0.1-SNAPSHOT.jar
   ```

3. **Docker Image**:
   ```bash
   docker run -p 8080:8080 ghcr.io/tadeasfort/pcpartsscraper:v1.0.0
   ``` 