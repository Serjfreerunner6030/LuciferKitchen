#!/system/bin/sh

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
 echo ".....Conversion completed!"
 echo
 else
 #echo
 echo ".....error: Conversion error."
 echo
 fi
}

 if [ -f ./"$file" ]; then
 echo
 echo ".....Enter the compression level (a number from 0 to 7):"
 myk
 else
 echo
 echo ".....There is no "$file" in the folder."
 echo
 fi




