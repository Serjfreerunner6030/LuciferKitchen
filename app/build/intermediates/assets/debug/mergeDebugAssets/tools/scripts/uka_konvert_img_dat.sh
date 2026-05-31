#!/system/bin/sh

 
 #img_name=$(echo $file | busybox sed 's!.*\/!!' | busybox awk -F"-|_|[+]|[.]|[{]|[(]" '{ print $1 }')

 #img_name=$(echo $file | busybox sed 's!.*\/!!' | busybox awk -F".img" '{ print $1 }')

myk(){
 read b
 if [ "$b" -le "7" ]; then
 echo ".....Converting to "$file".br...Please wait..."
 brotli -"$b"f ./"$file"
 echo
 else
 echo
 echo ".....Enter a number from 0 to 7:"
 myk
 echo
 fi
 if [ $(echo $?) -eq 0 ] ; then
 #echo
 echo ".....Conversion to .br completed successfully!"
 echo "$print_size_out"
 echo "$print_size_free"
 #echo
 busybox rm -f "$file"
 else
 #echo
 echo ".....error: Conversion to .br failed!"
 echo
 fi
return
}

br_conv()
{
 if [ -f ./"$file" ]; then
 echo
 echo ".....Converting to .br..."
 echo ".....Enter compression level (a number from 0 to 7):"
 myk
 else
 echo
 echo ".....No "$file" found in the folder."
 echo
 fi
return
}


 r="$(busybox basename $file)"
 img_name=${r%.*}

 if [ -f ./"$file" ]; then
 if [ ! -z "$(busybox hexdump -C -n 4 ./"$file" | grep '3a ff 26 ed')" ]; then
 if busybox test -s "$file"; then
 f_name="$(echo "$file" | busybox awk -F"." '{ print $1 }')"
size_out="$(busybox hexdump -C -n 50 "$file" | busybox awk '{if($1==00000000) {b="0x"$17$16$15$14}} {if($1==00000010) {a="0x"$5$4$3$2}}
END {print a*b}')"

 print_size_out="$(echo ".....Raw size == $size_out bytes")"

busybox dd if="$file" ibs=1 skip=40 of="$f_name".size.img bs=1 count=65536 &> /dev/null
size_free="$(busybox expr "$(tune2fs -l "$f_name".size.img 2> /dev/null | busybox awk '/Free blocks:/ { print $3 }')" \* 4096 / 1024 / 1024 2> /dev/null)"
 if [ $(echo $?) -eq 0 ]; then
 
 print_size_free=$(echo ".....Free space: $size_free MB")
 else
 print_size_free=$(echo ".....Free space: Undefined")
 fi
 fi

 echo
 echo ".....Converting "$file" -> "$img_name".new.dat..."
 echo
 echo ".....Enter the number corresponding to the Android version of the image being created:"
echo
 python31 /data/local/binary/bin_system/img2sdat-master/img2sdat5.py ./"$file"
if [ $(echo $?) -eq 0 ] ; then
echo
echo ".....Conversion to .dat completed successfully!"
#echo
 if [ "$br_check" == "true" ]; then
 file=./"$img_name".new.dat
 br_conv
 else
 echo "$print_size_out"
 echo "$print_size_free"
 #echo
 fi
else
echo
echo ".....error: Conversion to .dat failed!"
echo
fi

elif [ ! -z "$(busybox hexdump -C -n 2000 "$file" | busybox awk '/00000430/ { print $10$11 }' | busybox grep "53ef")" -o ! -z "$(busybox hexdump -C -n 20000 "$file" | busybox grep -o "30 50 4c 41")" -o ! -z "$(busybox hexdump -C -n 2000 "$file" | busybox awk '/00000400/ { print $2$3$4$5 }' | busybox grep -o "e2e1f5e0")" ]; then

if busybox test -s "$file"; then
 size_out="$(busybox stat -c %s "$file")"
 print_size_out="$(echo ".....Raw size == $size_out bytes")"
 size_free="$(busybox expr "$(tune2fs -l "$file" 2> /dev/null | busybox awk '/Free blocks:/ { print $3 }')" \* 4096 / 1024 / 1024 2> /dev/null)"
 if [ $(echo $?) -eq 0 ]; then
 
 print_size_free=$(echo ".....Free space: $size_free MB")
 else
 print_size_free=$(echo ".....Free space: Undefined")
 fi
 fi
echo
echo ".....Converting "$img_name".img -> "$img_name".sparse.img..."
#echo
img2simg ./"$file" ./"$img_name".sparse.img 2> /dev/nul
 if [ $(echo $?) -eq 0 ]; then
echo
echo ".....Conversion to sparse completed successfully!"
echo
 
 if [ ! -z "$(busybox hexdump -C -n 4 ./"$img_name".sparse.img | busybox grep '3a ff 26 ed')" ]; then
 #clear
 echo
 echo ".....Converting "$img_name".sparse.img -> "$img_name".new.dat..."
 echo
 echo ".....Enter the number corresponding to the Android version of the image being created:"
echo
 python31 /data/local/binary/bin_system/img2sdat-master/img2sdat5.py ./"$img_name".sparse.img
if [ $(echo $?) -eq 0 ]; then

 busybox mv -f ./"$img_name".sparse.new.dat ./"$img_name".new.dat
 busybox mv -f ./"$img_name".sparse.patch.dat ./"$img_name".patch.dat
 busybox mv -f ./"$img_name".sparse.transfer.list ./"$img_name".transfer.list
echo
echo ".....Conversion to .dat completed successfully!"
#echo
 if [ "$br_check" == "true" ]; then
 file=./"$img_name".new.dat
 br_conv
 else
 echo "$print_size_out"
 echo "$print_size_free"
 #echo
 fi
  else
echo
echo ".....error: Conversion to .dat failed!"
echo
 fi
 else
echo
echo ".....Error converting to sparse!"
echo
fi

else
echo
echo ".....Conversion of "$file" is not supported!"
echo
fi
fi
else
echo
echo ".....No image \""$file"\" found in the folder for conversion."
fi
 busybox rm -f /data/local/python31/tmp* tmp* "$img_name".sparse.img

unset size_out
unset size_free
unset print_size_free
unset print_size_out
busybox rm -f "$f_name".size.img


