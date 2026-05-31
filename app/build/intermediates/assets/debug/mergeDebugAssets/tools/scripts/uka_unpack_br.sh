#!/system/bin/sh

 cd /"$nb"

 #br_name=$(echo $file | busybox sed 's!.*\/!!' | busybox awk -F"-|[+]|[.]|[{]|[(]" '{ print $1 }')
 
  br_name=$(echo $file | busybox sed 's!.*\/!!' | busybox awk -F".new.dat.br" '{ print $1 }')


 if busybox test -s "$br_name".transfer.list; then
echo
echo ".....Converting "$br_name".new.dat.br..."
brotli -df "$br_name".new.dat.br -o "$br_name".new.dat 2> /dev/null

if [ $(echo $?) -eq 0 ]; then
echo
echo ".....Converting "$br_name".new.dat..."
echo
sdat2img "$br_name".transfer.list "$br_name".new.dat "$br_name".img
if [ $(echo $?) -eq 0 ]; then
echo
echo ".....Deleting "$br_name".new.dat..."
rm -f $PWD/"$br_name".new.dat
file=/"$nb"/"$br_name".img
nd="$nb"
. /data/local/binary/unpack_img
else
echo
echo ".....Error during conversion of "$br_name".new.dat"
echo
fi
else
echo
echo ".....Error during conversion of "$br_name".new.dat.br"
echo
fi
else
echo
echo ".....No file "$br_name".transfer.list in the folder."
 fi
