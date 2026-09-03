#!/usr/bin/env bash
#
# Lift this project out of the repository it currently sits in, into a
# standalone repository of its own — with its history, not a flat copy.
#
# The app has nothing to do with whatever else lives alongside it; this script
# exists so that separating them is one command rather than a careful manual
# copy that loses the commit history.
#
#   ./tools/extract-standalone-repo.sh git@github.com:<you>/trades-work-manager.git
#
# or, to build the standalone repo locally without pushing yet:
#
#   ./tools/extract-standalone-repo.sh
#
set -euo pipefail

REMOTE="${1:-}"
SUBTREE="tradesmanager"
OUTPUT="${OUTPUT:-../trades-work-manager}"

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

if [ ! -d "$SUBTREE" ]; then
  echo "error: expected to find $SUBTREE/ at the repository root ($repo_root)" >&2
  exit 1
fi

echo "==> Splitting $SUBTREE/ into a branch with only its own history"
# git subtree split rewrites the history so every commit that touched
# tradesmanager/ becomes a commit at the root of the new tree. Commits that
# never touched it are dropped.
split_ref="$(git subtree split --prefix="$SUBTREE" HEAD)"
echo "    $split_ref"

echo "==> Creating $OUTPUT"
rm -rf "$OUTPUT"
git init --quiet "$OUTPUT"
git -C "$OUTPUT" fetch --quiet "$repo_root" "$split_ref"
git -C "$OUTPUT" checkout --quiet -b main FETCH_HEAD

echo "==> Moving CI to the new repository root"
# In the host repository the workflow lives at its root and points at
# tradesmanager/**. Standalone, everything is at the root instead.
mkdir -p "$OUTPUT/.github/workflows"
if [ -f ".github/workflows/tradesmanager.yml" ]; then
  sed -e 's#tradesmanager/##g' \
      -e "s#^      - 'android/\*\*'#      - 'android/**'#" \
      ".github/workflows/tradesmanager.yml" > "$OUTPUT/.github/workflows/ci.yml"
  git -C "$OUTPUT" add .github/workflows/ci.yml
  git -C "$OUTPUT" -c user.name="$(git config user.name || echo Claude)" \
      -c user.email="$(git config user.email || echo noreply@anthropic.com)" \
      commit --quiet -m "ci: move the workflow to the standalone repository root"
fi

echo
echo "Standalone repository ready at: $(cd "$OUTPUT" && pwd)"
echo "Commits: $(git -C "$OUTPUT" rev-list --count HEAD)"
echo

if [ -n "$REMOTE" ]; then
  echo "==> Pushing to $REMOTE"
  git -C "$OUTPUT" remote add origin "$REMOTE"
  git -C "$OUTPUT" push -u origin main
  echo "Done."
else
  cat <<NEXT
Next, if you want it on GitHub:

  1. Create an EMPTY repository (no README, no .gitignore, no licence).
  2. cd $OUTPUT
     git remote add origin git@github.com:<you>/<repo>.git
     git push -u origin main

Then delete this project from the host repository:

  cd $repo_root
  git rm -r --quiet $SUBTREE .github/workflows/tradesmanager.yml
  git commit -m "Move the trades app into its own repository"
NEXT
fi
