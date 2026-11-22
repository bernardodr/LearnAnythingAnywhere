#!/usr/bin/env bash
# Sets up a fresh Ubuntu server (24.04 or newer) to run LAA. Run it once, as root, on the server:
#
#   git clone https://github.com/bernardodr/LearnAnythingAnywhere.git /opt/laa/src
#   /opt/laa/src/deploy/bootstrap.sh
#
# It's safe to run again: each step checks what's already done. A second run also refreshes compose.yml,
# compose.build.yml and deploy.sh in /opt/laa from the checkout, and never touches .env once it exists.
#
# What it does:
#   1. installs updates, and keeps installing security updates automatically (restarting at 04:30 UTC if one needs it);
#   2. turns off password sign-in for SSH, so only keys work (after checking that root has a key, so you can't be
#      locked out);
#   3. starts fail2ban, which bans an address for an hour after 5 failed SSH sign-ins in 10 minutes;
#   4. adds 2 GB of swap, so building the images (`deploy.sh build`) has room beside the running server;
#   5. installs Docker from Docker's own repository, with container logs capped at 100 MB each;
#   6. lays out /opt/laa: the deploy files, a backups folder, and .env from deploy/.env.example with the database
#      password, the sign-in secret and the owner setup code generated here. They're never printed or sent anywhere.
#   7. installs the nightly database backup (deploy/backup.sh and its timer, 03:15 UTC, 7 kept in /var/backups/laa)
#      and the laabackup user, whose only use is letting the owner's Mac fetch those backups (scripts/mac-backup.sh).
#
# Then you add what only you have to .env (your owner email, the AI key, the email password; the script lists them),
# and deploy: docs/DEPLOYING.md, "Setting up a new server".
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

LAA=/opt/laa
SRC="$LAA/src"

say() { printf '\n▸ %s\n' "$*"; }
fail() { printf '✗ %s\n' "$*" >&2; exit 1; }

[ "$(id -u)" = 0 ] || fail "Run this as root."
. /etc/os-release
[ "$ID" = ubuntu ] || fail "This script is for Ubuntu (found $ID)."
[ -d "$SRC/.git" ] || fail "Clone the repository to $SRC first (see the top of this script)."

# ---- 1. Updates, now and from now on.
say "1/7 Installing updates…"
apt-get update -q
# --force-confold keeps any configuration file already on the server rather than stopping to ask.
apt-get -q -y -o Dpkg::Options::=--force-confold upgrade
apt-get -q -y install ca-certificates curl git openssl unattended-upgrades fail2ban python3-systemd
cat >/etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
# Some security updates (the kernel) only take effect after a restart. The containers start again by themselves.
cat >/etc/apt/apt.conf.d/52laa-unattended-upgrades <<'EOF'
Unattended-Upgrade::Automatic-Reboot "true";
Unattended-Upgrade::Automatic-Reboot-Time "04:30";
Unattended-Upgrade::Remove-Unused-Kernel-Packages "true";
EOF

# ---- 2. SSH: keys only.
say "2/7 SSH: keys only…"
# Without a key for root, turning passwords off would lock everyone out.
grep -q -E '^(ssh-|ecdsa-)' /root/.ssh/authorized_keys 2>/dev/null \
  || fail "root has no SSH key in /root/.ssh/authorized_keys, so password sign-in stays on. Add a key and run this again."
# sshd uses the first value it reads for each setting, and reads this folder in name order, so 00- wins over any
# file the hosting provider adds.
cat >/etc/ssh/sshd_config.d/00-laa.conf <<'EOF'
# LAA (deploy/bootstrap.sh): sign in with a key only.
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin prohibit-password
EOF
sshd -t || { rm -f /etc/ssh/sshd_config.d/00-laa.conf; fail "The SSH configuration didn't pass sshd's own check, so it was taken back out."; }
# Ubuntu starts sshd only when a connection arrives (ssh.socket), so it may not be running to reload. If it isn't,
# the next connection starts it with this configuration anyway.
systemctl try-reload-or-restart ssh.service

# ---- 3. fail2ban for SSH.
say "3/7 fail2ban…"
cat >/etc/fail2ban/jail.d/laa.conf <<'EOF'
# LAA (deploy/bootstrap.sh): ban an address for an hour after 5 failed SSH sign-ins in 10 minutes.
[sshd]
enabled = true
backend = systemd
maxretry = 5
findtime = 10m
bantime = 1h
EOF
systemctl enable -q fail2ban
systemctl restart fail2ban

# ---- 4. Swap, for building images beside the running server.
say "4/7 Swap…"
if ! swapon --show=NAME --noheadings | grep -q '^/swapfile$'; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap -q /swapfile
  swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
fi
# Use the swap only when memory is really short, not as a matter of course.
echo 'vm.swappiness = 10' >/etc/sysctl.d/90-laa-swap.conf
sysctl -q -p /etc/sysctl.d/90-laa-swap.conf

# ---- 5. Docker, from Docker's own repository (https://docs.docker.com/engine/install/ubuntu/).
say "5/7 Docker…"
if ! command -v docker >/dev/null; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  cat >/etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $VERSION_CODENAME
Components: stable
Signed-By: /etc/apt/keyrings/docker.asc
EOF
  apt-get update -q
  apt-get -q -y install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
fi
# Container logs: Docker's compact format, at most 5 files of 20 MB per container, so logs can't fill the disk.
DAEMON='{ "log-driver": "local", "log-opts": { "max-size": "20m", "max-file": "5" } }'
if [ "$(cat /etc/docker/daemon.json 2>/dev/null)" != "$DAEMON" ]; then
  echo "$DAEMON" >/etc/docker/daemon.json
  systemctl restart docker
fi
systemctl enable -q docker

# ---- 6. /opt/laa.
say "6/7 /opt/laa…"
chmod 750 "$LAA"
install -m 640 "$SRC/deploy/compose.yml" "$SRC/deploy/compose.build.yml" "$LAA/"
install -m 750 "$SRC/deploy/deploy.sh" "$LAA/"
install -d -m 700 "$LAA/backups"
if [ ! -f "$LAA/.env" ]; then
  # Made private before anything secret goes in.
  install -m 600 "$SRC/deploy/.env.example" "$LAA/.env"
  set_secret() {
    local tmp; tmp="$(mktemp)"
    awk -v key="$1" -v value="$2" 'index($0, key "=") == 1 { print key "=" value; next } { print }' "$LAA/.env" >"$tmp"
    cat "$tmp" >"$LAA/.env"
    rm -f "$tmp"
  }
  set_secret POSTGRES_PASSWORD "$(openssl rand -hex 24)"
  set_secret JWT_SECRET "$(openssl rand -hex 32)"
  set_secret LAA_OWNER_SETUP_CODE "$(openssl rand -hex 16)"
  echo "Made $LAA/.env, with the database password, the sign-in secret and the owner setup code generated."
else
  echo "$LAA/.env already exists, so it was left as it is."
fi

# ---- 7. Nightly backups, and the user the owner's Mac fetches them as.
say "7/7 Nightly backups…"
if ! id laabackup >/dev/null 2>&1; then
  useradd --system --home-dir /var/lib/laabackup --create-home --shell /bin/sh laabackup
fi
# '*' means "no password", which isn't the same as a locked account: sshd still accepts the user's key.
usermod -p '*' laabackup
install -d -m 700 -o laabackup -g laabackup /var/lib/laabackup/.ssh
[ -f /var/lib/laabackup/.ssh/authorized_keys ] || install -m 600 -o laabackup -g laabackup /dev/null /var/lib/laabackup/.ssh/authorized_keys
install -d -m 750 -o root -g laabackup /var/backups/laa
# Root's and read-only to everyone else: the backup key's forced command runs this file.
install -d -m 755 /usr/local/lib/laa
install -m 755 "$SRC/deploy/backup.sh" /usr/local/lib/laa/backup.sh
install -m 644 "$SRC/deploy/laa-backup.service" "$SRC/deploy/laa-backup.timer" /etc/systemd/system/
systemctl daemon-reload
systemctl enable -q --now laa-backup.timer

# ---- What's left for you.
missing=""
for key in LAA_OWNER_EMAILS SPRING_MAIL_PASSWORD; do
  grep -q -E "^$key=.+" "$LAA/.env" || missing="$missing $key"
done
provider="$(awk -F= '$1 == "LAA_DEFAULT_PROVIDER" { print $2 }' "$LAA/.env")"
case "$provider" in
  OPENAI) grep -q -E '^OPENAI_API_KEY=.+' "$LAA/.env" || missing="$missing OPENAI_API_KEY" ;;
  ANTHROPIC) grep -q -E '^ANTHROPIC_API_KEY=.+' "$LAA/.env" || missing="$missing ANTHROPIC_API_KEY" ;;
  GOOGLE) grep -q -E '^GEMINI_API_KEY=.+' "$LAA/.env" || missing="$missing GEMINI_API_KEY" ;;
esac

printf '\n✓ The server is set up: %s, Docker %s.\n' "$PRETTY_NAME" "$(docker version --format '{{.Server.Version}}')"
if [ -n "$missing" ]; then
  printf '\nStill to fill in, in %s/.env (nano %s/.env):\n' "$LAA" "$LAA"
  for key in $missing; do printf '  - %s\n' "$key"; done
fi
[ ! -f /var/run/reboot-required ] || printf '\nSome updates need a restart: run `reboot` now, then sign in again after a minute.\n'
