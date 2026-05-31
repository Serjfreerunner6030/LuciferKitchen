#!/system/bin/sh


 size_output_raw()
{
ch_ext="$(busybox hexdump -C -n 2000 "$file" | busybox awk '/00000430/ { print $10$11 }' | busybox grep "53ef")"
 if busybox test -s "$file"; then
 size_out="$(busybox stat -c %s "$file")"
 if [ ! -z "$ch_ext" ]; then
 size_free="$(busybox expr "$(tune2fs -l "$file" 2> /dev/null | busybox awk '/Free blocks:/ { print $3 }')" \* 4096 / 1024 / 1024 2> /dev/null)"
 echo
 echo ".....Raw size == $size_out bytes"
 echo ".....Free space: $size_free MB"
 echo
 else
 echo
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
if [ -z "$(busybox hexdump -C -n 4 ./"$file" | grep '3a ff 26 ed')" ]; then
echo
echo ".....Converting..."
echo
img2simg ./"$file" ./"$r_name".sparse.img 2> /dev/nul
 if [ $(echo $?) -eq 0 ]; then
echo
echo ".....Sparse image created: \""$r_name".sparse.img\"!"
 size_output_raw
echo
 else
echo
echo ".....Error during conversion!"
echo
fi
else
echo
echo ".....The image is already sparse."
echo
fi
else
echo
echo ".....No image "$file" found in the folder."
echo
fi


