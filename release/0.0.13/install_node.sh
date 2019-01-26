#!/bin/bash

useradd node
usermod -a -G dialout node
rm -r /usr/share/node
cp -R  node /usr/share
chown -R node:node /usr/share/node
cp node.service /etc/systemd/system
systemctl daemon-reload
systemctl enable node
systemctl start node
