#!/system/bin/sh

 clear


part_num()
{
 clear
v=$(echo "boot" "recovery")

set -- $v
echo
echo ".....Select partition name:"
echo
select menu in $v "Exit to main menu"; do
case $REPLY in
                 [1-9]*) i="$#"
                      let i=i+1
                      file=$(echo "$@" | busybox cut -d' ' -f"$REPLY")
         if [ ! -z "$file" -a "$REPLY" -lt "$i" ]; then
 part_name="$file"
         return
         break 
         elif [ "$REPLY" == "$i" ]; then
        clear
        #main_menu
        return
        break
        else
        clear
        echo
            echo "      Enter the numbers corresponding to the menu."
         part_num
         return
         break
         fi
         break;;
   *) clear
        echo
            echo "      Enter the numbers corresponding to the menu."
         part_num
         return
         break;;
  esac
 done
return
}


key_algoritm()
{
 clear
v=$(echo "RSA2048" "RSA4096" "RSA8192")

set -- $v
echo
echo ".....Select signing algorithm:"
echo
select menu in $v "Exit to main menu"; do
case $REPLY in
                 [1-9]*) i="$#"
                      let i=i+1
                      file=$(echo "$@" | busybox cut -d' ' -f"$REPLY")
         if [ ! -z "$file" -a "$REPLY" -lt "$i" ]; then
 sign_print="--algorithm $(echo "SHA256_RSA$file" | busybox sed 's!RSA!!')"
         return
         break 
         elif [ "$REPLY" == "$i" ]; then
        clear
        #main_menu
        return
        break
        else
        clear
        echo
            echo "      Enter the numbers corresponding to the menu."
         key_algoritm
         return
         break
         fi
         break;;
   *) clear
        echo
            echo "      Enter the numbers corresponding to the menu."
         key_algoritm
         return
         break;;
  esac
 done
return
}


aik_pack()
 {
 #avb_dir=/data/local/UnpackerSystem
if busybox test "$(avbtool info_image --image "$file_real" 2> /dev/null)"; then
 size_sign="$(busybox cat "$avb_dir"/avb.img | busybox awk '/Image size:/ { print $3 }')"
 part_name="$(busybox cat "$avb_dir"/avb.img | busybox awk '/Partition Name:/ { print $3 }')"
 prop_avb="$(busybox cat "$avb_dir"/avb.img | busybox awk '/    Prop:/ { print "--prop"" "$2":"$4 }' | tr '\n' ' ')"
 
 check_alg="$(busybox cat "$avb_dir"/avb.img | busybox awk '/^Algorithm:/ { print $2 }')"

if [ "$check_alg" != "NONE" -a ! -z "$check_alg" ]; then
 sign_print="--algorithm $check_alg"
 else
 key_algoritm
fi
else

size_sign="$size_sign"
part_num        #Function


fi

 size_footer="$(avbtool add_hash_footer --partition_size "$size_sign" --calc_max_image_size)"
 
 truncate -s "$size_footer" "$file_real"

 echo "avbtool add_hash_footer --image "$file_real" --partition_size "$size_sign" "$prop_avb"--partition_name "$part_name" "$sign_print" --key "$pack_file"" > "$avb_dir"/avb.sh
 clear
 echo 
 echo ".....Signing...."
 echo
 chmod 755 "$avb_dir"/avb.sh && "$avb_dir"/avb.sh
 if [ $(echo $?) -eq 0 ]; then
clear
 echo
 echo ".....AVB structure successfully created"
 check_alg="$(avbtool info_image --image "$file_real" | busybox awk '/^Algorithm:/ { print $2 }' 2> /dev/null)"

if [ "$check_alg" != "NONE" -a ! -z "$check_alg" ]; then
 echo ".....Successfully signed $file_real"
 return
else
 echo
 echo ".....AVB structure created without signing $file_real"
 busybox rm -f "$avb_dir"/avb.sh "$avb_dir"/avb.img
 return
 fi
 else
 echo
 echo ".....Error creating AVB structure"
busybox rm -f "$avb_dir"/avb.sh "$avb_dir"/avb.img
 return
 fi
 return
}


search_key()
{
          #clear
          echo
          echo ".....Go to the folder where the private signing key is located, for example: cd /sdcard"
read b && $b
if [ $(echo $?) -eq 0 ]; then
clear
nd=$(echo $b | busybox awk '{ print $2 }' | busybox sed 's!^/!!')
 echo
 echo ".....Searching for keys..."

 v=$(for a in $(busybox find -maxdepth 1 -size -10k -type f -exec busybox grep -rl "PRIVATE KEY" {} \;); do
     echo "$a"
     done)

set -- $v

if [ ! -z "$v" ]; then
clear
 check_alg="$(busybox cat "$avb_dir"/avb.img 2> /dev/null | busybox awk '/^Algorithm:/ { print $2 }')"
  if [ "$check_alg" != "NONE" -a ! -z "$check_alg" ]; then
  echo
  echo ".....Algorithm: $check_alg"
  fi
echo
echo ".....Select key:"
echo
select menu in $v "Exit to main menu"; do
case $REPLY in
                 [1-9]*) i="$#"
                        j="$#"
                      let i=i+1
                      let j=j+2
                      file=$(echo "$@" | busybox cut -d' ' -f"$REPLY")
         if [ ! -z "$file" -a "$REPLY" -lt "$i" ]; then
 clear
 name_key="$(echo "$file" | busybox sed 's!^\./!!')"
 pack_file=/"$nd"/"$name_key"

  aik_pack
         return
         break 
         elif [ "$REPLY" == "$i" ]; then
         clear
        #main_menu
        return
        break
        else
        clear
        echo
            echo "      Enter the numbers corresponding to the menu."
         search_key
         return
         break
         fi
         break;;
   *) clear
        echo
            echo "      Enter the numbers corresponding to the menu."
         search_key
         return
         break ;;
  esac
 done
 else
 clear
 echo
 echo ....."No suitable keys for signing in folder \"/$nd\"."
 echo
return
fi
echo
else
echo
echo ".....error: Directory change error!"
echo
echo ".....Please enter the directory correctly!"
echo
search_key
return
fi
return
}


my_size_sign()
{
 echo
 echo ".....Enter the size of the stock image in MB"
 echo
 read a && set -- "$a"  
 if [ $(echo $?) -eq 0 ]; then
 if busybox test "$(busybox expr "$a" \* 1024 \* 1024 2> /dev/null)"; then
 size_sign="$(busybox expr "$a" \* 1024 \* 1024 2> /dev/null)"
 set -- $size_sign
 return
else
clear
echo
 echo ".....Enter the size correctly"
echo
my_size_sign
return
fi
 else
 echo
 echo ".....Error!"
 echo
 my_size_sign
 return
 fi
 return
}


sign_image()
{     
              #clear
              echo
              echo "         Signing images     "

              echo
              echo "..Go to the folder where the \".img\" file for signing is located, for example: cd /sdcard"
read b && $b
if [ $(echo $?) -eq 0 ]; then
clear
nd=$(echo $b | busybox awk '{ print $2 }' | busybox sed 's!^/!!')
echo
echo ".....Searching for files..."

 v=$(for a in $(busybox find . -maxdepth 1 -name '*.img' -type f | busybox sed 's!./!!'); do
 if [ ! -z "$(busybox hexdump -C -n 4500 "$a" 2> /dev/null | busybox grep -o '41 4e 44 52 4f 49 44 21')" ]; then
     echo "$a"
 fi
     done)

set -- $v

if [ ! -z "$v" ]; then
clear
echo
echo ".....Select a file:"
echo
select menu in $v "Exit to main menu" "Exit"; do
case $REPLY in
                 [1-9]*) i="$#"
                        j="$#"
                      let i=i+1
                      let j=j+2
                      file=$(echo "$@" | busybox cut -d' ' -f"$REPLY")
         if [ ! -z "$file" -a "$REPLY" -lt "$i" ]; then
 clear
file=/"$nd"/"$file"
file_real="$file"
avb_dir=/data/local/UnpackerSystem
if busybox test "$(avbtool info_image --image "$file" 2> /dev/null)"; then
 
 avbtool info_image --image "$file" > "$avb_dir"/avb.img
 else
 my_size_sign
 clear
 key_algoritm
 clear
 fi
  search_key
    main_menu
        return
         break 
         elif [ "$REPLY" == "$i" ]; then
         clear
        main_menu
        return
        break
        elif [ "$REPLY" == "$j" ]; then
         clear
        return
        break
        else
        clear
        echo
            echo "      Enter the numbers corresponding to the menu."
         sign_image
         return
         break
         fi
         break;;
   *) clear
        echo
            echo "      Enter the numbers corresponding to the menu."
         sign_image
         return
         break ;;
  esac
 done
 else
 clear
 echo
 echo ....."No \".img\" files for signing in the folder \"/$nd\"."
 echo
 main_menu
return
fi
echo
else
echo
echo ".....error: Directory change error!"
echo
echo ".....Please enter the directory correctly!"
echo
sign_image
return
fi
return
}
sign_image

busybox rm -f "$avb_dir"/avb.sh "$avb_dir"/avb.img