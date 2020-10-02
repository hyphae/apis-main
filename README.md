# apis-main

## Introduction
apis-mainはSony CSLが開発した自律分散制御の電力相互融通ソフトウェアである。    
apis-mainは各ノード毎にインストールされ、定期的に自身のBattery残容量を取得し、  
Battery残容量によって決められた行動ルールに従ってapis-main間でネゴシエーションを  
行って自動でノード間の電力融通を実現するソフトウェアである。  
apis-main間のネゴシエーションはイーサネットなどのコミュニケーションラインが用いられ  
電力融通はDC Grid上で直流にて行われる。  
apis-mainは集中管理制御のように中央で制御を行うソフトウェアは存在せず、すべて同一の     
ソフトウェアであるapis-mainのみで自律分散制御が行われる。  


## Getting Started


## Usage


## Documentation
[apis-main specification](https://github.com/oes-github/apis-main/blob/master/doc/jp/apis-main_specification.md)


## License
[Apache License Version 2.0](https://github.com/oes-github/apis-main/blob/master/LICENSE)


## Notice
[Notice](https://github.com/oes-github/apis-main/blob/master/NOTICE.md)
