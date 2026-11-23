"""Require the push CI run and its aggregate build check on the exact release commit."""
import json
import os
import subprocess
import sys
import time


def api(path):
    return json.loads(subprocess.check_output(["gh", "api", path], text=True))


def ready(repo, sha):
    root = f"repos/{repo}/actions"
    runs = api(f"{root}/workflows/ci.yml/runs?event=push&head_sha={sha}&per_page=100")["workflow_runs"]
    matching = [run for run in runs if run["head_sha"] == sha and run["event"] == "push"
                and run["head_repository"]["full_name"] == repo]
    if not matching:
        raise ValueError(f"No push CI run for {sha}. Push this commit to a branch and let CI finish.")
    latest = max(matching, key=lambda run: run["id"])
    if latest["status"] != "completed":
        return False
    if latest["conclusion"] != "success":
        raise ValueError(f"CI for {sha} ended with {latest['conclusion']}. See {latest['html_url']}.")
    jobs = api(f"{root}/runs/{latest['id']}/jobs?filter=latest&per_page=100")["jobs"]
    gates = [job["conclusion"] for job in jobs if job["name"] == "Release readiness"]
    if gates != ["success"]:
        raise ValueError(f"Missing successful Release readiness check for {sha}. See {latest['html_url']}.")
    print(f"Verified Release readiness for {sha} in {latest['html_url']}.")
    return True


if __name__ == "__main__":
    deadline = time.monotonic() + 900
    try:
        sha = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        while not ready(os.environ["GITHUB_REPOSITORY"], sha):
            if time.monotonic() >= deadline:
                sys.exit("CI is still pending. Retry after Release readiness has passed.")
            time.sleep(15)
    except ValueError as error:
        sys.exit(str(error))
