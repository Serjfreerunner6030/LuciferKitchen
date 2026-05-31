#!/system/bin/sh


size_output_raw()
{
ch_ext="$(busybox hexdump -C -n 2000 "$r_name".raw.img | busybox awk '/00000430/ { print $10$11 }' | busybox grep "53ef")"
 if busybox test -s "$r_name".raw.img; then
 size_out="$(busybox stat -c %s "$r_name".raw.img)"
 if [ ! -z "$ch_ext" ]; then
 size_free="$(busybox expr "$(tune2fs -l "$r_name".raw.img | busybox awk '/Free blocks:/ { print $3 }')" \* 4096 / 1024 / 1024)"
 echo ".....Raw size == $size_out bytes"
 echo ".....Free space: $size_free MB"
 echo
 else
 echo ".....Raw size == $size_out bytes"
 echo
fi
fi
 return
}


#r_name=$(echo $file | busybox sed 's!.*\/!!' | busybox awk -F"-|_|[+]|[.]|[{]|[(]" '{ print $1 }')

 r="$(busybox basename $file)"
 r_name=${r%.*}


 if [ -f ./"$file" ]; then
if [ ! -z "$(busybox hexdump -C -n 4 ./"$file" | grep '3a ff 26 ed')" ]; then
echo
echo ".....Converting..."
echo
 simg2img ./"$file" ./"$r_name".raw.img
 if [ $(echo $?) -eq 0 ]; then
echo ".....Raw image created: \""$r_name".raw.img\"!"
 size_output_raw
echo
 else
 echo
 echo ".....Error during conversion!"
 echo
 fi
 else
 echo
 echo ".....The image is not sparse."
 echo
 fi
 else
 echo
 echo ".....No image "$file" found in the folder."
 echo
 fi


