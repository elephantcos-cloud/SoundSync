#!/bin/bash
set -e

REPO="https://github.com/elephantcos-cloud/SoundSync.git"
BRANCH="main"

echo ""
echo "╔══════════════════════════════════════╗"
echo "║   SoundSync → GitHub Push Script     ║"
echo "╚══════════════════════════════════════╝"
echo ""

# 1. Install git if missing
if ! command -v git &>/dev/null; then
    echo "📦 Installing git..."
    pkg install git -y
fi

# 2. Go to project folder
cd "$(dirname "$0")"
echo "📁 Working in: $(pwd)"

# 3. Configure git identity (change if you want)
git config --global user.email "elephantcos@github.com"
git config --global user.name "elephantcos-cloud"

# 4. Init + remote
git init
git remote remove origin 2>/dev/null || true
git remote add origin "$REPO"

# 5. Stage all files
git add -A
git status

# 6. Commit
git commit -m "🎵 SoundSync — WiFi Direct audio streaming app" 2>/dev/null || \
git commit --allow-empty -m "🎵 SoundSync update"

# 7. Push
git branch -M "$BRANCH"
echo ""
echo "🚀 Pushing to GitHub..."
echo "   (You will be asked for your GitHub username & Personal Access Token)"
echo ""
git push -u origin "$BRANCH" --force

echo ""
echo "✅ Done! Check: https://github.com/elephantcos-cloud/SoundSync"
echo "   GitHub Actions will build the APK automatically."
