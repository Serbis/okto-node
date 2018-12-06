#!/bin/bash

UserExist()
{
   awk -F":" '{ print $1 }' /etc/passwd | grep -x $1 > /dev/null
   return $?
}

UserExist 'node'
if [ $? = 0 ]; then
   useradd node
   usermod -a -G dialout node
   echo "Created 'node' user"
fi

rm -r /usr/share/node
cp -R  node /usr/share
chown -R node:node /usr/share/node
cp node.service /etc/systemd/system
systemctl daemon-reload
systemctl enable node
systemctl start node
