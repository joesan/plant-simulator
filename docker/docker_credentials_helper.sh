#!/usr/bin/env bash

set -x

echo
echo "-----------------------------------------------------"
echo "Setting up docker credential pass"
echo "-----------------------------------------------------"

#sudo add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
#sudo apt update
#sudo apt -y install docker-ce pass
sudo apt-get install pass
#echo 'DOCKER_OPTS="--experimental"' | sudo tee /etc/default/docker
#sudo service docker restart

#curl -fsSlL https://github.com/docker/docker-credential-helpers/releases/tag/v0.6.3/docker-credential-pass-v0.6.3-amd64.tar.gz | sudo tar xf - -C /usr/local/bin
wget https://github.com/docker/docker-credential-helpers/releases/download/v0.6.0/docker-credential-pass-v0.6.0-amd64.tar.gz && tar -xf docker-credential-pass-v0.6.0-amd64.tar.gz && chmod +x docker-credential-pass && sudo mv docker-credential-pass /usr/local/bin/
mkdir -p "$HOME"/.docker
echo '{ "credsStore": "pass" }' | tee "$HOME"/.docker/config.json
gpg --batch --gen-key <<-EOF
%echo generating a standard key
Key-Type: DSA
Key-Length: 1024
Subkey-Type: ELG-E
Subkey-Length: 1024
Name-Real: GitHub Actions CI
Name-Email: github-actions@github.com
Expire-Date: 0
%commit
%echo done
EOF
key=$(gpg --no-auto-check-trustdb --list-secret-keys | grep ^sec | cut -d/ -f2 | cut -d" " -f1)
pass init "$key"