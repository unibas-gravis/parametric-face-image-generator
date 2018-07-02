Bootstrap: docker
From: ubuntu:16.04
%post
   apt update 
   apt install -y software-properties-common python-software-properties
   add-apt-repository -y ppa:webupd8team/java
   apt update
   echo debconf shared/accepted-oracle-license-v1-1 select true | \
   debconf-set-selections
   echo debconf shared/accepted-oracle-license-v1-1 seen true | \
   debconf-set-selections
   apt install -y oracle-java8-installer
   apt install -y libx11-6 libxxf86vm1 libxrender1 libxtst6 libxi6 libxrandr2 libxcursor1 libgl1-mesa-glx libsm6 libxt6
