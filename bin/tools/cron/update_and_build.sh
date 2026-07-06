#!/bin/bash

# cron
# */10 * * * * ~/workspace/pulsar/update_and_build.sh >> ~/workspace/pulsar/cron.log 2>&1

# 获取当前脚本所在的目录
AppHome="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 向上查找包含 VERSION 文件的父目录
while [[ "$repoRoot" != "/" && ! -f "$repoRoot/ROOT.md" ]]; do
  AppHome="$(dirname "$repoRoot")"
done

# 切换到工作目录
cd $repoRoot || exit

# Print the current time
echo
date

# 更新代码库
git pull

# 检查是否有更新
if git status | grep -q 'Your branch is up to date'; then
    echo "No updates found."
else
    echo "Updates found. Building & Testing ..."
    "$repoRoot"/bin/build.sh -clean -test
fi
