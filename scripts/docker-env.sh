# Resolve Docker socket for local dev (Colima on macOS) vs Linux (EC2 / docker engine).
if [ -z "${DOCKER_HOST:-}" ]; then
  if [ -S "${HOME}/.colima/default/docker.sock" ]; then
    export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
  elif [ -S /var/run/docker.sock ]; then
    export DOCKER_HOST="unix:///var/run/docker.sock"
  fi
fi
