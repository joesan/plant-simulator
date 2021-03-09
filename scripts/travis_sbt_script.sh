#!/usr/bin/env bash
# update this only when sbt-the-bash-script needs to be updated
export SBT_LAUNCHER=1.4.8
export SBT_OPTS="-Dfile.encoding=UTF-8"
curl -L --silent "https://github.com/sbt/sbt/releases/download/v$SBT_LAUNCHER/sbt-$SBT_LAUNCHER.tgz" > $HOME/sbt.tgz
tar zxf $HOME/sbt.tgz -C $HOME
sudo rm /usr/local/bin/sbt
sudo ln -s $HOME/sbt/bin/sbt /usr/local/bin/sbt
