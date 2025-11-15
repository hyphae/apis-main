**apis-main仕様書**
====================

**Rev 0.69**

**改版履歴**

<table><thead><tr class="header"><th><strong>日付</strong></th><th><strong>Rev.</strong></th><th><strong>担当</strong></th><th><strong>変更内容</strong></th></tr></thead><tbody><tr class="odd"><td>2020/4/17</td><td>0.5</td><td>岩瀬</td><td>初回Release</td></tr><tr class="even"><td>2020/4/20</td><td>0.6</td><td>岩瀬</td><td>OES Member Comment修正</td></tr><tr class="odd"><td>2020/4/21</td><td>0.61</td><td>岩瀬</td><td>OES Member Comment修正第二弾</td></tr><tr class="even"><td>2020/4/28</td><td>0.62</td><td>岩瀬</td><td>“8.Grid Master Data収集”、”9.3 電力融通Log”の項目不備のため追記</td></tr><tr class="odd"><td>2020/9/18</td><td>0.63</td><td>岩瀬</td><td><p>用語にEZA2500の説明を追記</p><p>P.18 5.1 apis-main – Device Driver間Web APIの誤記修正</p></td></tr><tr class="even"><td>2020/9/24</td><td>0.64</td><td>岩瀬</td><td><p>用語にdcdc_batt_commの説明を追記</p><p>P.18 5.1 apis-main – Device Driver間Web APIに各Web API利用時に期待する戻り値を追記</p></td></tr><tr class="odd"><td>2020/9/25</td><td>0.65</td><td>岩瀬</td><td>P.18 5.1 apis-main – Device Driver間Web APIに/dcdc/set系のWeb API利用時に期待する戻り値を追記</td></tr><tr class="even"><td>2020/9/29</td><td>0.66</td><td>岩瀬</td><td><p>P.19 5.1 apis-main – Device Driver間Web API についてapis-mainの制御に使用していないWeb APIを削除し、/dcdc/set系のWeb API利用時に期待する戻り値について修正</p><p>P.48 誤記修正</p></td></tr><tr class="odd"><td>2020/9/29</td><td>0.67</td><td>岩瀬</td><td>目次のPage番号振り直し</td></tr><tr class="even"><td>2020/10/5</td><td>0.68</td><td>岩瀬</td><td><p>P.37の以下の項目の説明を修正</p><p>・controller.dataAcquisitionPeriodMsec</p><p>・controller.retryLimit</p><p>P.47の8. Grid Master収集データのstatus.operationModeに情報を追記</p></td></tr><tr class="odd"><td>2020/10/5</td><td>0.69</td><td>岩瀬</td><td>P.47の8. Grid Master収集データのstatus.operationModeに情報を修正</td></tr></tbody></table>

**目次**
========

[**1.** **用語・略語** 6][]

[**2.** **概要** 7][]

[**ソフトウェア構成** 11][]

[**3.1. ソフトウェアアーキテクチャ** 11][]

[**3.2.** **apis-main内Service構成** 11][]

[**4.** **動作詳細説明** 13][]

[**4.1.** **クラスタ構築** 13][]

[**4.2.** **Grid Master選定** 13][]

[**4.3.** **apis-main処理** 14][]

[**4.4.** **Grid Master処理** 15][]

[**4.5.** **電力融通処理** 16][]

[**4.6.** **各種ロック処理** 17][]

[**5.** **通信仕様について** 19][]

[**5.1.** **apis-main – Device Driver間Web API** 19][]

[**5.2.** **apis-main – Device Driver間の各get 系Web APIで期待される戻り値** 20][]

[**5.2.1.** **“/dcdc/get” で期待される戻り値** 20][]

[**5.2.2.** **“/dcdc/get/status” で期待される戻り値** 21][]

[**5.2.3.** **“/dcdc/set?dig=&lt;Grid電流&gt;” で期待される戻り値** 22][]

[**5.2.4.** **“/dcdc/set?dvg=&lt;Grid電圧&gt;&drg=&lt;GridDroop率&gt;” で期待される戻り値** 22][]

[**5.2.5.** **“/dcdc/set?mode=&lt;Mode&gt;&dvg=&lt;Grid電圧&gt;&dig=&lt;Grid電流&gt;&dig=&lt;GridDroop率&gt;” で期待される戻り値** 23][]

[**5.2.6.** **“/battery/get” で期待される戻り値** 24][]

[**5.2.7.** **“/all/get” で期待される戻り値** 24][]

[**5.2.8.** **“/version/get” で期待される戻り値** 26][]

[**5.3.** **Device Driver – ハードウェア間通信仕様** 27][]

[**6.** **設定ファイルについて** 28][]

[**6.1.** **config.json** 28][]

[**6.2.** **hwConfig.json** 30][]

[**6.3.** **scenario.json** 31][]

[**6.4.** **policy.json** 35][]

[**6.5.** **cluster.xml** 39][]

[**6.6.** **logging.properties** 39][]

[**6.7.** **start.sh** 40][]

[**6.8.** **stop-kill.sh** 40][]

[**6.9.** **key.pem** 41][]

[**6.10.** **cert.pem** 41][]

[**7.** **DC Grid制御機能** 42][]

[**7.1.** **電圧Referenceの選定** 42][]

[**7.2.** **電圧Ramp Up** 42][]

[**7.3.** **電圧Reference権限獲得動作** 42][]

[**7.4.** **電流補正機能** 43][]

[**7.5.** **Constant Voltage(CV)移動** 44][]

[**7.6.** **電圧最適化** 45][]

[**8.** **Grid Master Data収集** 47][]

[**9.** **Log取得・保存機能** 50][]

[**9.1.** **apis-main動作Log** 50][]

[**9.2.** **apis-main動作Log出力先** 51][]

[**9.3.** **電力融通Log** 52][]

[**10.** **Error処理・保護機能** 54][]

[**10.1.** **apis-main Error処理** 54][]

[**11.** **その他の機能** 58][]

[**11.1.** **トポロジー検出** 58][]

[**11.2.** **Gateway機能** 64][]

[**12.** **安全確認機能** 66][]

[**12.1.** **Global Safety Check** 66][]

[**12.2.** **Local Safety Check** 66][]

[**12.2.1.静的Local Safety Check** 66][]

[**12.2.2. 動的Local Safety Check** 68][]

[**13.** **セキュリティ** 69][]

[**13.1.** **apis-main間通信セキュリティ** 69][]

[**13.2.** **Device Driver アクセス制限** 69][]

[**14.** **プライバシー** 69][]

[**15.** **OSSライセンス** 70][]

[**16.** **動作環境** 71][]

[**16.1.** **ハードウェア要求** 71][]

[**16.2.** **OS要求** 71][]

**用語・略語**
==============

| **用語**         | **説明**                                                                                                                                                                     |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| apis-main        | Sony CSLが開発した電力相互融通ソフトウェアの名称である。                                                                                                                     |
| PP2P             | Physical Peer to Peerの略である。ブロックチェーン等による台帳管理による電力取引ではなく物理的に特定の相手間での電力交換ができるという意味を込めてP2Pと分けた表現にしている。 |
| Grid Master      | APISに含まれるService名称でDC Gridを制御し電力融通を実現する。                                                                                                               |
| Hazelcast        | 複数のマシンにDataを分散し並列処理を行うことでアプリケーションを高速化するインメモリ分散コンピューティング運用ライブラリである。                                             |
| Vert.x           | 負荷分散プログラムのフレームワーク。Hazelcastライブラリを利用してEvent Busをクラスタ化しネットワーク上で負荷分散処理を行う。                                                 |
| Event Bus        | Vert.xのプログラムの最小単位をVerticleと呼び。そのVerticle間でお互いに通信するための非同期インターフェースをEvent Busと呼ぶ                                                  |
| 共有メモリ       | Hazelcastの機能でクラスタにある共有情報。クラスタに参加しているAPISは参照・編集・削除が可能である。                                                                          |
| EZA2500          | 本文ではTDKラムダ製EZA-2500-32048のカスタマイズ品を指す。                                                                                                                    |
| dcdc\_batt\_comm | Sony CSLが開発したEZA2500を制御しBattery情報を取得するDevice Driverである。                                                                                                  |
|                  |                                                                                                                                                                              |

**概要**
========

apis-mainはSony CSLが開発した自律分散制御の電力相互融通ソフトウェアの名称である。apis-mainは各ノード(双方向DC/DC Converter 経由でDC Gridと接続された蓄電システム)毎にインストールされ、Device Driver経由で定期的に自身のBattery残容量を取得し、Battery残容量によって決められた行動ルールに従ってapis-main間でネゴシエーションを行い自動でノード間の電力融通を実現するソフトウェアである。(この行動ルールとは他のapis-mainへ自ら充放電Requestを送るのか、他のapis-mainから受け取った充放電Requestに対してAcceptを返すのかなどの判断を自身のBatteryの残容量を元に決めるためのルールを指す。) apis-main間のネゴシエーションはEthernetなどのコミュニケーションラインが用いられ、電力融通はDC Grid上で直流にて行われる。apis-mainは集中管理制御のように中央で制御を行うソフトウェアは存在せず、すべて同一のソフトウェアであるapis-mainのみで自律分散制御が行われる。 (apis-mainはGrid Masterと呼ばれるアドホックな集中管理ソフトウェアを立てて制御を行う。Grid Masterは予め設定されたルールに基づいてどのapis-mainでも立てられる。)

<img src="media/media/image1.png" style="width:4.03333in;height:1.74892in" />

<p align="center">図2-1</p>

    
Battery間の電力融通例を図2-2に示す。Battery AからBattery Bへ電力を融通する場合にはapis-mainがDevice Driver経由で各ノードの双方向DC/DC ConverterのModeについて放電側をConstant Current Mode(CC Mode)に、充電側をConstant Voltage Mode(CV Mode)に制御することによって電力融通が行われる。

<img src="media/media/image3.png" style="width:4.90833in;height:1.71884in" />
<p align="center">図2-2</p>

次にapis-main間のネゴシエーションの流れの例を以下に示す。 (図2-3参照)

1.  apis-mainはDevice Driver経由で定期的に自身のBattery残容量を取得する。

2.  1.で取得したBattery残容量を自身の行動ルールAと比較する。

3.  行動ルールAと比較した結果、必要であれば他のapis-mainにRequestを発行する。

4.  Requestを受けた他のapis-mainは自身のBattery残容量を取得する。

5.  4.で取得したBattery残容量を自身の行動ルールBと比較する。

6.  行動ルールBと比較した結果、Requestに承認可能であればRequest発行元にAcceptを発行する。

7.  Requestを出したapis-mainは返ってきた複数のAcceptから適切なものを一つ選択する。(選択アルゴリズムは6.3 scenario.json参照)

8.  Requestと選択されたAcceptから電力融通取引情報を作成する。

<img src="media/media/image4.png" style="width:5.9in;height:2.45833in" />
<p align="center">図2-3</p>

単体のapis-mainがコミュニケーションラインとDC Gridに接続された様子を図2-4に示す。

<img src="media/media/image5.png" style="width:5.01294in;height:2.025in" />
<p align="center">図2-4</p>

次に複数のapis-mainによる構成を図2-5に示す。

<img src="media/media/image6.png" style="width:5.15452in;height:2.01667in" />
<p align="center">図2-5</p>

apis-mainは図2-6のように複数の電力融通を並行して行うことが可能である。

<img src="media/media/image7.png" style="width:5.0125in;height:1.93279in" />
<p align="center">図2-6</p>

DC Gridを制御する上での注意点は図2-7のようにDC Grid上にCV ModeのDC/DC Converterが２台以上存在すると電圧の衝突が発生することである。

<img src="media/media/image8.png" style="width:5.9in;height:2.29167in" />
<p align="center">図2-7</p>

そのため図2-8のようにDC Grid上にCV ModeのDC/DC Converterは1台のみとし、他のDC/DC ConverterはすべてCC Modeで制御を行う。

<img src="media/media/image9.png" style="width:5.9in;height:2.24167in" />
<p align="center">図2-8</p>

上記の制御を行うためにapis-mainにはGrid MasterというGridを制御するServiceが存在する。apis-mainは起動時にコミュニケーションライン経由で通信を行ってクラスタを形成し、予め決められたルールに従ってクラスタに1台だけGrid Masterを選定する。Grid MasterはDevice Driver経由でクラスタ内の全DC/DC Converterを制御しDC Grid経由で電力融通を行わせる。全apis-mainがGrid Masterになることができ、決められたルールに従って動的にGrid Masterの移動を行うことも可能である。

<img src="media/media/image10.png" style="width:5.9in;height:2.28333in" />
<p align="center">図2-9</p>


**ソフトウェア構成**
====================

**3.1. ソフトウェアアーキテクチャ**
-----------------------------------

図3-1はソフトウェアアーキテクチャを示した図である。Linux OS上にJDK(Java Development Kit)、その上にイベントドリブンの負荷分散プラットフォームであるVert.xとインメモリ分散コンピューティングのHazelcastの2つのフレームワークを使用してapis-mainを動作させている。apis-mainとDevice Driver間はWeb API経由にて情報の受け渡しを行う。(動作確認済みOSSソフトウェアのVersionは15.OSSライセンス参照)

<img src="media/media/image13.png" style="width:3.63333in;height:2.81838in" />
<p align="center">図3-1</p>

**apis-main内Service構成**
--------------------------

電力融通を実現するソフトウェアであるapis-mainと、BatteryやDC/DC Converterとのやり取りを行うDevice Driverで構成しており、apis-mainは以下の4つのServiceに分けられる。(図3-2参照)

1.  User Service

Battery残容量毎のBattery充放電要求(“2.製品概要”では行動ルールとして説明)を記したファイルをScenarioファイルと呼び、そのScenarioファイルの内容と現在のBattery残容量を比較することで充放電に関する要求を判定する。充放電が必要と判定した場合はMediator Serviceに対して他のapis-mainとネゴシエーションを行うように要求する。

2.  Mediator Service

User Serviceの要求に基づき、他のapis-mainとネゴシエーションを行い、電力融通取引情報を作成する。必要に応じてGrid Masterを起動させる役割も担う。

3.  Grid Master Service

自身もしくは他のapis-mainから成立した電力融通取引情報を受け取り、電力融通に必要なDC/DC Converterを制御して電力融通を行わせる。電力融通中は融通された電力量を監視し、電力融通取引情報で決められた電力融通量に到達後融通を止める。電力融通記録は電力融通を行った双方のapis-mainが動作するハードウェアの不揮発性メモリに保存される。

4.  Controller Service

User Serviceの要求でDC/DC ConverterやBatteryの情報を取得する。

また、自身もしくは他のapis-main上のGrid Master Serviceからの要求でDC/DC Converterを制御し電力融通を行わせる。

<img src="media/media/image14.png" style="width:5.00152in;height:2.78333in" />
<p align="center">図3-2</p>

**動作詳細説明**
================

4.1**クラスタ構築**
----------------

apis-mainは起動時にHazelcastと呼ばれるVert.xフレームワークが使用するクラスタリングマネージャを用いてコミュニケーションライン上に存在する複数のapis-mainとクラスタを構築する。同一クラスタに参加するためには設定ファイルであるcluster.xml上で同一クラスタ名を指定する必要がある。

**Grid Master選定**
-------------------

apis-mainが起動するとMediator Service のGird Master Management機能が、ランダムなタイミングでクラスタ内にGrid Masterの存在を問い合わせる。起動直後等でクラスタ内にGrid Masterが存在しない場合には、設定ルールに従って、適切なGrid Masterを選定する。Grid Master選定には "voltageReference", "fixed", "anywhere"の3種類の選定方法がある。

1.  voltageReference

> 電力融通時に電圧Reference(CV Mode)となるノードがGrid Masterになる選定方法である。電圧Referenceが変わればそれに伴ってGrid Masterも移動する。
>
> 電圧Referenceとなるノードは、電力融通開始時に決定されるためapis-main起動直後にはGrid Masterは決まらない。そのため起動時はGrid Masterの存在を問い合わせ、返信がなければ自らがGrid Masterになろうとする。(複数のGrid Masterの同時起動を防ぐため、自身のGrid Master Serviceを起動させる前にランダム時間待ち、その後再びGrid Masterの不在が確認された場合は自らGrid Master Serviceを起動させる。)

1.  fixed　

> Grid Masterになるノードを固定する選定方法である。fixedでGrid Masterを選定する場合はクラスタ内で最初にfixedで指定したノードのapis-mainを起動させる必要がある。

1.  anywhere　

> 最初にapis-mainが起動したノードがGrid Masterとなる選定方法である。後に不具合等でGrid Masterが不在になった場合には不在を検知したノードが自らGrid Masterになろうとする。

**apis-main処理**
-----------------

apis-mainはループ処理として以下の内容を継続して行う。

　1.Event BusにHelloメッセージを送信し、自身と同じIDがクラスタに存在しないことを確認する。(存在した場合はError処理として自身(apis-main)をShutdownさせる。)

> 2.自身のLocal Error情報をキャッシュから取り出して処理を行う。(apis-mainは発生したすべてのError情報をEvent Bus上の全ノードに送信する。Error情報を受信したノードは自身かつLocal Error情報のみを自身のキャッシュに保存する。Global Error情報はGrid Masterが起動しているノードのキャッシュに保存される。具体的なErrorの内容とその処理方法は後述する。Local ErrorとGlobal Error及びそれらのError処理に関しては10.1. apis-main Error処理参照)
>
> 3.Grid MasterにGlobal Error情報を確認する。

4.自身のSettingを確認する。(Settingの種類は以下)

Run: 電力融通生成を有効にする電力融通稼働時の標準Settingである。

> Soft Stop: 既存の電力融通は実施するが、新たな電力融通生成は行わない。

Force Stop: 既存の電力融通を停止させ、新たな電力融通生成も行わない。

Manual: 検査等でDC/DC Converter等をManualで動作させるためのModeである。

5.Controller Service経由で自身のDC/DC ConverterやBatteryの情報を取得する。

> 6.Local Safety Checkを行う。(12.2 Local Safety Check参照)

7.自身のScenario情報を取得する。

> 8.自身のBattery情報とScenario情報と比較する。比較した結果、過不足ありと判定した場合はUser ServiceはMediator Service に指示を出しクラスタ内の全apis-mainに電力融通Requestを発信する。
>
> 9.他のapis-mainから電力融通Requestを受け取ったMediator Service は電力融通Requestを 自身のUser Service に渡す。User Serviceは⾃ノードのBattery残容量とScenarioファイルを元に電力融通Requestに応じられるかを評価し、応じられる場合は電力融通可能な電力量を計算しその結果をAccept情報としてRequest元に返信する。応じられない場合はRequestを無視してAccept情報は返信しない。
>
> 10.電力融通Request元の Mediator Service は自身のUser Serviceに処理を渡して他のapis-mainから返信された電力融通可能電力量を元に、Scenarioファイルにて指定された選択アルゴリズムに従って、最適な電力融通相⼿のノードを選択させ、その後電⼒融通の情報が記載された 電力融通取引情報をHazelcastの共有メモリに登録し、Grid Masterの処理を待つ。

1.  1.  **Grid Master処理**
        -------------------

Grid Master はループ処理として以下の内容を継続して行う。

1.Grid Masterを起動する前にHazelcastの共有メモリに自身のIDを登録して他のapis-mainがGrid Masterにならないようにロックをする。(Grid Masterを移動する際は共有メモリから登録したIDを削除する。)

2.クラスタ内にGrid Masterが重複していないことを確認するために、Event BusにHelloメッセージを送信する。重複が確認された場合はError処理を行う。

3.各apis-mainからEvent Bus上に発行されるGlobal Error情報をキャッシュに保存する。

4.Global Error情報をキャッシュから取り出しError処理を行う。(10. Error処理・保護機能参照)

5.全apis-mainからDC/DC ConverterとBatteryの情報を取得する。(8. Grid Master Data収集参照)

6.クラスタ全体の全ノード数やDC Grid上の平均Battery残量等を算出する。

7.Global Safety Checkを行う。(12.1 Global Safety Check参照)

8.Hazelcastの共有メモリに登録された電力融通取引情報に基づき順次電力融通を実施する。

9.Grid Masterの移動が発生した場合は適切なノードにGrid Masterを移動させる。

> (Grid Masterが移動する際に既存の融通情報など引き継ぐ情報はすべてHazelcastの共有メモリ上に存在するため旧新Grid Master間で通信し情報を引き継ぐ必要はなく、新Grid Masterは起動後、共有メモリ上のGrid Masterの情報を参照し動作を開始する。)

**電力融通処理**
----------------

Hazelcastの共有メモリに登録された電力融通取引情報は以下の5つの状態を持つ。

“not activated”, “activated”, “started”, “stopped”, “deactivated”

Grid Masterは共有メモリ上に登録されている全電力融通取引情報を集め、それぞれの状態に合わせて一つずつ、最適な処理を実行する。

\(1\) not activated 　

電力融通が行われておらずDC Gridの電圧がRamp Upしていない状態を示す。

> 共有メモリに登録された電力融通取引情報がこのステータスの場合にはGrid Masterは電圧Reference側のapis-mainに対してDC/DC ConverterをCV Modeに設定するように指示する。CV Modeに設定されたDC/DC ConverterはDC Gridを指定された電圧までRamp Upさせる。Ramp Upが完了した場合はステータスを(2)のactivatedへ移行させる。 (7.2 電圧Ramp Up参照)

\(2\) activated

> DC Grid の電圧Ramp upが完了し電力融通が開始できる状態を示す。既に電力融通が行われておりDC Gridの電圧のRamp Upが完了している場合には新しく登録された電力融通取引情報のステータスはactivatedになる。共有メモリに登録された電力融通取引情報がこのステータスの場合にはGrid Masterは電力融通を実施するapis-mainのDC/DC Converterをそれぞれ適切なCC Modeに設定し、(3)のstartedへ移行させる。

\(3\) started

> 共有メモリに登録された電力融通取引情報がこのステータスの場合には既に電力融通が開始されていることを示す。Grid Masterは自身のループ処理の中で電力融通した電力量の累積が目標の電力量に達していることを確認する。達していれば、放電ノードのapis-mainに対してDC/DC ConverterのModeをWaitに設定するように指示し電力融通を止めてステータスを(4)のstoppedへ移行させる。

\(4\) stopped

> 共有メモリに登録された電力融通取引情報がこのステータスの場合には既に電力融通した電力量の累積が目標の電力量に達し、放電側のDCDC ConverterがWait Modeになっていることを示す。Grid Masterは充電ノードのapis-mainに対してDC/DC ConverterのModeをWaitに設定するように指示し、ステータスを(5)のdeactivateに移行させる。その際、他に継続して電力融通が行われており、CV ModeになっていたDC/DC Converterの電力融通を停止する場合にはCV Modeの移動を実施 する。 (7.5 Constant Voltage(CV) 移動参照)

\(5\) deactivate

> 共有メモリに登録された電力融通取引情報がこのステータスの場合には既に電力融通が完了したことを示す。放電ノードと充電ノードの双方に電力融通結果をファイルとして書き込み、電力融通情報を共有メモリから削除する。(最終的な 電力融通結果の保存は 電力融通処理の最後に行われるが、電力融通中も放電側と充電双方のノードにその時点での電力融通情報をファイルとして保存する。)

**各種ロック処理**
------------------

Hazelcastの共有メモリや各ノードのLocalメモリを使用してデータの整合性を保つために同時アクセス制限を行う排他ロックや、ある一定の条件が整わないと他の動作を制限するインタロック機能があり、それらについて以下に説明する。

1.  共有メモリ上のロック

> ・GMインタロック

　　 Grid Master Serviceを起動する前に共有メモリ上に自身のIDを設定することで別の

ノードがGrid Master Serviceを起動しないようにロックをかける。

　　 Grid Masterを移動する際はGrid Master Serviceを終了させた後、共有メモリ上のGMインタロックを解除し新しいノードのIDを設定してロックを行った上で移動する。

1.  Localメモリ上のロック

> ・電力融通インタロック
>
> Grid Masterが電力融通を行う両端のノードに対してかけるロックである。DC/DC
>
> Converterの最大電流量と１電力融通の電流量で電力融通数を決めている。
>
> ・電力融通インタロック用排他ロック
>
> 電力融通インタロックの取得/解放は、非同期で行われるが、整合性を取るために

排他ロックで同期を取る。

> ・データ取得排他ロック

　　　apis-mainからDevice Driverに対してデータを取得する際にLocalにあるデータキャッシュの上書き競合を防ぐため、この排他ロックを使用してデータ取得用Commandと制御用Commandを制御する。all/getと /dcdc/get/statusと/dcdc/setが排他制御されている。

> ・GM処理ループ排他ロック

　　　Grid MasterのMain loop実行中にGrid Masterの停止が発生しないようにGrid Masterの移動(起動及び停止)とGrid MasterのMain Loop処理を排他制御するために使用する。

　　・共有メモリ電力融通情報読み書き排他ロック

　　　自ノード内の複数スレッドによる共有メモリへの同時書き込みを防ぐために使用する。

1.  ファイルシステム上のロック

> ・融通方向インタロック
>
> 同一ノード上の複数のプロセス間で排他制御を実現するための排他ロックである。

　 後述するGateway機能で同一ノード上に複数のプロセス(apis-main)を起動させる際に使用する。ファイルシステムを利用することでプロセス間(apis-main間)の排他制御を行う。(11.2 Gateway機能参照)

　　　<img src="media/media/image15.png" style="width:4.24242in;height:2.1946in" />
   <p align="center">図4-1</p>


1.  **通信仕様について**
    ====================

    1.  **apis-main – Device Driver間Web API**
        --------------------------------------

apis-main とDevice DriverはWeb APIにて情報のやり取りを行う。以下にそのWeb APIの仕様を説明する。

<table><thead><tr class="header"><th><p>DC/DC Converter</p><p>制御</p></th><th>/dcdc/get</th><th>情報取得</th></tr></thead><tbody><tr class="odd"><td></td><td>/dcdc/get/status</td><td>ステータス情報のみ取得</td></tr><tr class="even"><td></td><td>/dcdc/set?dig=&lt;Grid電流値&gt;</td><td>DC Grid側電流値設定</td></tr><tr class="odd"><td></td><td>/dcdc/set?dvg=&lt;Grid電圧値&gt; &amp;drg=&lt;Grid Droop率&gt;</td><td>DC Grid側電圧値&amp;Droop値設定</td></tr><tr class="even"><td></td><td><p>/dcdc/set?mode=&lt;Mode&gt;</p><p>&amp;dvg=&lt;Grid電圧値&gt;</p><p>&amp;dig=&lt;Grid電流値&gt; &amp;drg=&lt;Grid Droop率&gt;</p></td><td>DC Grid側Mode&amp;電圧値&amp;電流値&amp;Droop値設定</td></tr><tr class="odd"><td>Battery情報取得</td><td>/battery/get</td><td>情報取得</td></tr><tr class="even"><td>全Device情報取得</td><td>/all/get</td><td>/dcdc/get +/battery/get情報取得</td></tr><tr class="odd"><td>Version情報取得</td><td>/version/get</td><td>Device Driver Version情報取得</td></tr></tbody></table>

1.  **apis-main – Device Driver間の各get 系Web APIで期待される戻り値**
    ------------------------------------------------------------------

    1.  ###  **“/dcdc/get” で期待される戻り値**

　以下に/dcdc/getのWeb API利用時に期待されるDevice Driverからの戻り値のパラメータを示す。(各パラメータの説明に関しては“8. Grid Master Data収集”の表のdcdcの項目を参照のこと)

&lt;例&gt;

{

"status" : {

"status" : "0x0000",　　　　　　

"alarmState" : "No alarm",　　　

"operationMode" : "Waiting"　　

},

"meter" : {

"wg" : -2.44,

"tmp" : 29.59,

"vb" : 51.47,

"wb" : -12.21,

"vg" : 358.27,

"ib" : -0.24,

"ig" : 0

},

"vdis" : {

"dvg" : 349.99,

"drg" : 0

},

"param" : {

"dig" : 3,

}

}

### **“/dcdc/get/status” で期待される戻り値**

　以下に/dcdc/get/statusのWeb API利用時に期待されるDevice Driverからの戻り値のパラメータを示す。(各パラメータの説明に関しては“8. Grid Master Data収集”の表のdcdcの項目を参照のこと)

&lt;例&gt;

{

  "status" : {

"status" : "0x0000",　

      "alarmState" : "No alarm",

      "operationMode" : "Waiting"

  },

  "meter" : {

      "wg" : -2.44,

      "tmp" : 30.08,

      "vb" : 51.45,

      "wb" : -12.21,

      "vg" : 358.29,

      "ib" : -0.23,

      "ig" : 0

  }

}

### **“/dcdc/set?dig=&lt;Grid電流&gt;” で期待される戻り値**

　以下に/dcdc/set?dig=&lt;Grid電流&gt;のWeb API利用時に期待されるDevice Driverからの戻り値のパラメータを示す。(各パラメータの説明に関しては“8. Grid Master Data収集”の表のdcdcの項目を参照のこと)

&lt;例&gt;

{

"param": {

"dig": 7.8125,

},

}

### **“/dcdc/set?dvg=&lt;Grid電圧&gt;&drg=&lt;GridDroop率&gt;” で期待される戻り値**

　以下に/dcdc/set?dvg=&lt;Grid電圧&gt;&drg=&lt;GridDroop率&gt;のWeb API利用時に期待されるDevice Driverからの戻り値のパラメータを示す。(各パラメータの説明に関しては“8. Grid Master Data収集”の表のdcdcの項目を参照のこと)

&lt;例&gt;

{

"vdis": {

"dvg": 299.98291015625,

},

}

###  **“/dcdc/set?mode=&lt;Mode&gt;&dvg=&lt;Grid電圧&gt;&dig=&lt;Grid電流&gt;&dig=&lt;GridDroop率&gt;” で期待される戻り値**

　以下に/dcdc/set?mode=&lt;Mode&gt;&dvg=&lt;Grid電圧&gt;&dig=&lt;Grid電流&gt;&drg=&lt;GridDroop率&gt;のWeb API利用時に期待されるDevice Driverからの戻り値のパラメータを示す。(各パラメータの説明に関しては“8. Grid Master Data収集”の表のdcdcの項目を参照のこと)

&lt;例&gt;

{

"status": {

"status": "0x0002",

"operationMode": "Heteronomy CV",

},

"meter": {

"vg": 380,

},

"vdis": {

"dvg": 380,

},

"param": {

"dig": 7.8125,

},

}

### **“/battery/get” で期待される戻り値**

　以下に/battery/getのWeb API利用時に期待されるDevice Driverからの戻り値のパラメータを示す。(各パラメータの説明に関しては“8. Grid Master Data収集”の表のbatteryの項目を参照のこと)

&lt;例&gt;

{

"rsoc" : 70,

"battery\_operation\_status" : 3

}

### **“/all/get” で期待される戻り値**

　以下に/all/getのWeb API利用時に期待されるDevice Driverからの戻り値のパラメータを示す。(各パラメータの説明に関しては“8. Grid Master Data収集”のdcdcとbatteryの項目を参照のこと)

&lt;例&gt;

{

"dcdc" : {

{

"status" : {

"status" : "0x0000",

"alarmState" : "No alarm",

"operationMode" : "Waiting"

},

"meter" : {

"wg" : -2.44,

"tmp" : 29.59,

"vb" : 51.47,

"wb" : -12.21,

"vg" : 358.27,

"ib" : -0.24,

"ig" : 0

},

"vdis" : {

"dvg" : 349.99,

"drg" : 0

},

"param" : {

"dig" : 3,

},

"battery" : {

{

"rsoc" : 70,

"battery\_operation\_status" : 3

}

}

### **“/version/get” で期待される戻り値**

　以下に/version/getのWeb API利用時に期待されるDevice Driverからの戻り値のパラメータを示す。

“6.1 config.json”の “systemType” が”dcdc\_v2”でdcdc\_batt\_comm利用設定の場合はapis-mainは起動時に/version/getのWeb APIを発行する。その際にDevice Deriverから以下のパラメータが戻らない場合にはapis-mainはShutdownする。

&lt;例&gt;

{

"comm\_protocol\_version" : "1.0"

"dcdc\_batt\_comm\_version" : "1.0 "

}

・Version情報について

Device Driverはapis-mainから/version/get コマンドを受けると以下2つの情報を返す。

"comm\_protocol\_version" : "1.0"

"dcdc\_batt\_comm\_version" : "1.0"

common\_protocol\_versionはapis-mainとの通信プロトコルのVersionを示しておりdcdc\_batt\_comm\_versionはDevice DriverのVersionを示している。

各情報は下記のようにmajor versionとminor versionをピリオドで分けた表記になる。

　“comm\_protocol\_version” : \[major version\] . \[minor version\]　　　&lt;例&gt; “1.0”

apis-mainとDevice Driver間のインターフェースを定義することで、各ソフトウェアのVersionに関連なく動作が可能な構造にすることを目的としている。

各Web APIコマンド(/dcdc/getや/battery/get等)に対してDevice Driverから返されるパラメータに追加や削除、変更が発生した場合で、かつ、apis-main側に何らかの対応が必要な場合はmajor versionを上げる。apis-main側の対応が不要な場合はminor versionを上げる。apis-mainは通信プロトコルのVersionとしてmajor versionのみを持ち、起動時に一度/version/getコマンドを発行しDevice Driverよりcomm\_protocol\_versionを取得する。apis-main は取得したcomm\_protocol\_versionのmajor versionと自身の持つmajor versionを比較しapis-main側のmajor versionがcomm\_protocol\_versionのmajor versionと比較して等しいかそれより大きいときに起動処理を継続し、小さい場合には組み合わせに不都合があるとしてFATALでapis-mainをShutdownする。 　　

起動処理継続 : apis-main major ver. ≧ dcdc\_batt\_comm major ver.

　　apis-main Shutdown : apis-main major ver. &lt; dcdc\_batt\_comm major ver.

**Device Driver – ハードウェア間通信仕様**
------------------------------------------

ハードウェア環境の違いはDevice Driverによって吸収する。Device Driver –ハードウェア間の通信は環境によって異なる為、各ハードウェアの通信仕様書を参照し適切にDevice Driverを開発する。

**設定ファイルについて**
========================

apis-mainには複数の設定ファイルや鍵ファイル等が存在する。それらのファイルについて説明する。

**config.json**
---------------

json形式のファイルで以下のノード固有の基本情報を設定する。apis-main起動時に一度だけ読み込まれるためパラメータを変更した場合はapis-mainの再起動が必要となる。

<table><thead><tr class="header"><th>programId</th><th>プログラム識別文字列</th></tr></thead><tbody><tr class="odd"><td>comminityId</td><td>コミュニティ識別文字列で1つ以上のクラスタをまとめる上位概念のID、clusterId及びapis-mainのVersion文字列と共に暗号化のSeedとして用いられる</td></tr><tr class="even"><td>clusterId</td><td>クラスタ識別文字列でcomminityId及びapis-mainのVersion文字列と共に暗号化のSeedとして用いられる</td></tr><tr class="odd"><td>security.enable</td><td>共有メモリ暗号化とEvent Bus SSL化の有効/無効設定</td></tr><tr class="even"><td>security.pemKeyFile</td><td>Event Bus SSL化に使われる秘密鍵</td></tr><tr class="odd"><td>security.pemCertFile</td><td>Event Bus SSL化に使われる証明書</td></tr><tr class="even"><td>unitId</td><td>ノード識別文字列</td></tr><tr class="odd"><td>unitName</td><td>ノード名称</td></tr><tr class="even"><td>serialNumber</td><td>ノードシリアル番号　(IoT Board管理番号として使用可能)</td></tr><tr class="odd"><td>systemType</td><td><p>ハードウェアの種類</p><p>dcdc_emulator : ハードウェアエミュレータ</p><p>dcdc_v1 : 旧Device Driver dcdc_controller 用</p><p>dcdc_v2 : dcdc_batt_comm用</p></td></tr><tr class="even"><td>hwConfigFile</td><td>hwConfigファイル保存先</td></tr><tr class="odd"><td>policyFile</td><td>Policyファイル保存先</td></tr><tr class="even"><td>scenarioFile</td><td>Scenarioファイル保存先</td></tr><tr class="odd"><td>stateFileFormat</td><td>Local Operation Mode保存先&amp;フォーマット</td></tr><tr class="even"><td>dealLogDirFormat</td><td>電力融通Log保存先&amp;フォーマット</td></tr><tr class="odd"><td><p>fileSystemExclusive</p><p>LockFileFormat</p></td><td>ファイルシステムを用いたプロセス間排他制御で用いるロックファイルの保存先及びフォーマット</td></tr><tr class="even"><td>policyFileReadTimeoutMsec</td><td>PolicyファイルのRead Timeout設定</td></tr><tr class="odd"><td>connection.emulator.host</td><td>systemTypeがdcdc_emulatorの場合のハードウェアエミュレータのIP Address</td></tr><tr class="even"><td>connection.emulator.port</td><td>systemTypeがdcdc_emulatorの場合のハードウェアエミュレータのPort番号</td></tr><tr class="odd"><td><p>connection.dcdc_controller</p><p>.host</p></td><td>systemTypeがdcdc_v1の場合のdcdc_controller用IP Address情報、もしくはdcdc_v2の場合のdcdc_batt_comm用IP Address</td></tr><tr class="even"><td><p>connection.dcdc_controller</p><p>.port</p></td><td>systemTypeがdcdc_v1の場合のdcdc_controller用Port番号、もしくはdcdc_v2の場合のdcdc_batt_comm用Port番号</td></tr><tr class="odd"><td>connection.emu_driver.host</td><td>systemTypeがdcdc_v1の場合のEMU Driver用IP Address</td></tr><tr class="even"><td>connection.emu_driver.port</td><td>systemTypeがdcdc_v1の場合のEMU Driver用Port番号</td></tr><tr class="odd"><td>controlCenter.enabled</td><td>Service Centerの有効/無効設定</td></tr><tr class="even"><td>controlCenter.account</td><td>Service Centerへのログインアカウント</td></tr><tr class="odd"><td>controlCenter.password</td><td>Service Centerへのログインパスワード</td></tr><tr class="even"><td><p>controlCenter.account</p><p>.encrypted</p></td><td><p>Service Centerへの暗号化されたログインアカウント</p><p>(項目毎の個別暗号化用)</p></td></tr><tr class="odd"><td><p>controlCenter.password</p><p>.encrypted</p></td><td><p>Service Centerへの暗号化されたログインパスワード</p><p>(項目毎の個別暗号化用)</p></td></tr><tr class="even"><td><p>controlCenter.encrypted_</p><p>.enabled</p></td><td><p>Service Centerへの暗号化された有効/無効設定</p><p>(controlCenter.encrypted 以下全体暗号化用)</p></td></tr><tr class="odd"><td><p>controlCenter.encrypted_</p><p>.account</p></td><td><p>Service Centerへの暗号化されたログインアカウント</p><p>(controlCenter.encrypted 以下全体暗号化用)</p></td></tr><tr class="even"><td><p>controlCenter.encrypted_</p><p>.password</p></td><td><p>Service Centerへの暗号化されたログインパスワード</p><p>(controlCenter.encrypted 以下全体暗号化用)</p></td></tr><tr class="odd"><td>watchdog.enabled</td><td>apis-mian Alive情報有効無効設定</td></tr><tr class="even"><td>watchdog.periodMsec</td><td>Watch Dog Reset周期　(ms)</td></tr><tr class="odd"><td>watchdog.host</td><td>Watch DogがperiodMsec間隔でAccessするIP Address</td></tr><tr class="even"><td>watchdog.port</td><td>Watch DogがperiodMsec間隔でAccessするPort番号</td></tr><tr class="odd"><td>watchdog.uri</td><td>Watch DogサービスのURI</td></tr><tr class="even"><td><p>watchdog.requestTimeout</p><p>Msec</p></td><td>Watch DogのTimeout時間(ms)</td></tr><tr class="odd"><td><p>batteryCapacityManagement</p><p>.enabled</p></td><td>Battery容量管理機能有効/無効設定</td></tr></tbody></table>

**hwConfig.json**
-----------------

json形式のファイルでノード固有のハードウェア情報を設定する。refreshingPeriodMsecで設定された間隔毎にファイルの再読み込みが行われるためapis-mainを再起動することなくパラメータを動的に変更することが可能である。

<table><thead><tr class="header"><th>refreshingPeriodMsec</th><th>hwConfigファイル再読み込み間隔(ms)</th></tr></thead><tbody><tr class="odd"><td>batteryNominalCapacityWh</td><td>Battery容量(Wh)</td></tr><tr class="even"><td>gridCurrentCapacityA</td><td>自ノードがDC Gridに充放電可能な最大電流(A)</td></tr><tr class="odd"><td>gridCurrentAllowanceA</td><td>自ノードが充放電する電流のDC Gridに対する誤差(A)</td></tr><tr class="even"><td>droopRatio</td><td>CV移動時のDroop率　(%)</td></tr><tr class="odd"><td><p>efficientBatteryGrid</p><p>valtageRatio</p></td><td><p>DC/DC Converterの効率が最大となる入出力電圧比N</p><p>設定</p></td></tr><tr class="even"><td>safety.range.dcdc.meter.tmp.min</td><td>Local Safety Check : DC/DC Converter温度最小値 (℃)</td></tr><tr class="odd"><td>safety.range.dcdc.meter.tmp.max</td><td>Local Safety Check : DC/DC Converter 温度最大値 (℃)</td></tr><tr class="even"><td>safety.range.dcdc.meter.vg.min</td><td>Local Safety Check : DC Grid電圧最小値 (V)</td></tr><tr class="odd"><td>safety.range.dcdc.meter.vg.max</td><td>Local Safety Check : DC Grid電圧最大値 (V)</td></tr><tr class="even"><td>safety.range.dcdc.meter.vb.min</td><td>Local Safety Check : Battery電圧最小値 (V)</td></tr><tr class="odd"><td>safety.range.dcdc.meter.vb.max</td><td>Local Safety Check : Battery電圧最大値 (V)</td></tr><tr class="even"><td>safety.range.dcdc.meter.ig.min</td><td>Local Safety Check : DC Grid電流最小値 (A)</td></tr><tr class="odd"><td>safety.range.dcdc.meter.ig.max</td><td>Local Safety Check : DC Grid電流最大値 (A)</td></tr><tr class="even"><td>safety.range.dcdc.meter.ib.min</td><td>Local Safety Check : Battery電流最小値 (A)</td></tr><tr class="odd"><td>safety.range.dcdc.meter.ib.max</td><td>Local Safety Check : Battery電流最大値 (A)</td></tr></tbody></table>

**scenario.json**
-----------------

json形式のファイルでノード毎に独自に定義することが可能な電力融通のためのRequestを設定する。refreshingPeriodMsecで設定された間隔毎にファイルの再読み込みが行われるためapis-mainを再起動することなくパラメータを動的に変更することが可能である。

<table><thead><tr class="header"><th>refreshingPeriodMsec</th><th>Scenarioファイル再読み込み間隔 (ms)</th></tr></thead><tbody><tr class="odd"><td>acceptSelection.storategy</td><td><p>電力融通Requestに対して複数のAcceptが届いた際の選択アルゴリズムを設定する。</p><p>“amount” :</p><p>電力融通可能である電力量が最も多いノードを選択する。</p><p>“pointAndAmount” :</p><p>電力融通に売買概念の導入や、電力融通Requestの優先度を調整する場合に設定する。</p><p>・自ノードが充電側の場合</p><p>　　最低価格を提示したノードを選択する。</p><p>・自ノードが放電側の場合</p><p>　　最高価格を提示したノードを選択する。</p><blockquote><p>＊価格が同じ場合は電力融通可能な電力量が最も多いノードを選択する。</p></blockquote></td></tr><tr class="even"><td><p>“00:00:00-24:00:00” : {</p><p>}</p></td><td><p>Scenario有効の時間帯設定</p><p>左は24時間同じScenarioを利用する際の設定を示している。 例として12時間毎にScenario設定を切り替える場合は以下のようにする。</p><p>“00:00:00-12:00:00” : {</p><p>}</p><p>“12:00:00-24:00:00” : {</p><p>}</p><p>時間帯を区切ることで例えば1時間毎にScenarioを分けるなどの使い方が可能である。</p><p>{}の中にその時間帯で利用するScenarioを記述する。</p></td></tr></tbody></table>

<table><thead><tr class="header"><th><p>“request” : {</p><p>“excess” : {</p><p>“discharge” : {</p><p>“pairUnitId” : “E002”</p><p>“limitWh” : 3360</p><p>“pointPerWh”:10</p><p>}</p><p>}</p><p>“sufficient” : {</p><p>}</p><p>“scarce” : {</p><p>}</p><p>“short” : {</p><p>“charge” : {</p><p>“pairUnitId” : “E002”</p><p>“limitWh” : 1440</p><p>“pointPerWh” : 10</p><p>}</p><p>}</p></th><th><p>他のapis-mainに充放電Requestを出す条件を記載する。</p><p>・”excess”</p><blockquote><p>自ノードのBattery残容量が3360Whになるまで放電Requestを発行する。また、その際の取引条件として10 point以上と提示する。</p></blockquote><p>・”sufficient”の状態</p><p>　 充放電要求せず</p><p>・”scarce”　</p><p>充放電要求せず</p><p>・”short”</p><blockquote><p>自ノードのBattery残容量が1440Whになるまで充電Requestを発行する。また、その際の取引条件として10 point以下と提示する。</p></blockquote><p>“pairUnitId”を指定することによりそのノードとのみ取引を行う。</p></th></tr></thead><tbody><tr class="odd"><td><p>“accept” : {</p><p>“excess” : {</p><p>“discharge” : {</p><p>“pairUnitId” : “E002”</p><p>“limitWh” : 2400</p><p>“pointPerWh”:10</p><p>}</p><p>}</p><p>“sufficient” : {</p><p>“discharge” : {</p><p>“pairUnitId” : “E002”</p><p>“limitWh” : 2400</p><p>“pointPerWh”:10</p><p>}</p><p>}</p><p>“scarce” : {</p><p>“charge” : {</p><p>“pairUnitId” : “E002”</p><p>“limitWh” : 2400</p><p>“pointPerWh”:10</p><p>}</p><p>}</p><p>“short” : {</p><p>“charge” : {</p><p>“pairUnitId” : “E002”</p><p>“limitWh” : 2400</p><p>“pointPerWh” : 10</p><p>}</p><p>}</p></td><td><p>他のapis-mainからの充放電RequestにAcceptを出す条件を記載する。</p><p>・”excess”</p><p>自ノードのBattery残容量が2400Whになるまで他のapis-mainの放電Requestに対してAcceptを行う。また、その際の取引条件として10 point以上と提示する。</p><p>・”sufficient”の状態</p><p>自ノードのBattery残容量が2400Whになるまで他のapis-mainの放電Requestに対してAcceptを行う。また、その際の取引条件として10 point以上と提示する。</p><p>・”scarce”　</p><p>自ノードのBattery残容量が2400Whになるまで他のapis-mainの充電Requestに対してAcceptを行う。また、その際の取引条件として10 point以下と提示する。</p><p>・”short”</p><p>自ノードのBattery残容量が2400Whになるまで他の他のapis-mainの充電Requestに対してAcceptを行う。また、その際の取引条件として10 point以下と提示する。</p></td></tr></tbody></table>

Battery容量の最大が4800Whとし上記の設定を行った際のScenarioは図6-1のようになる。

“pairUnitId”を指定することによりそのノードとのみ取引を行う。

<img src="media/media/image16.png" style="width:3.152in;height:4.21022in" />

　　　　　　　※取引pointはすべて10 point
<p align="center">図2-9</p>


**policy.json**
---------------

json形式のファイルでシステム全体の秩序を維持するために必要な制御アルゴリズムとそのアルゴリズムを安全に実行するために必要なパラメータを設定する。

refreshingPeriodMsecで設定された間隔毎にファイルの再読み込みが行われるためapis-mainを再起動することなくパラメータを動的に変更することが可能である。(動的更新機能は機能としては存在するが、通常の運用では全apis-mainの停止後にpolicy.jsonを変更する。)

このファイルはクラスタの中で共通の設定を持つ必要があるため整合性監視機能がある。クラスタ生成時、最初に起動したapis-mainがHazelcastの共有メモリ上に自身のpolicy.jsonを置き、その後クラスタに参加するapis-mainは共有メモリに置かれたpolicy.jsonと自身が持つpolicy.jsonを比較し、全てのパラメータが一致しなければ自身(apis-main)を停止させることにより整合性を取る。

<table><thead><tr class="header"><th>refreshingPeriodMsec</th><th>Policyファイル再読み込み間隔 (ms)</th></tr></thead><tbody><tr class="odd"><td>operationMode</td><td><p>クラスタ全体のOperation Mode設定</p><p>autonomous : 通常の電力融通Mode</p><p>heteronomous : 既存電力融通継続</p><p>新電力融通生成無効</p><p>stop : 電力融通停止Mode</p><p>manual : 手動Mode (評価用)</p></td></tr><tr class="even"><td>memberUnitIds</td><td>クラスタの全ノードのID情報</td></tr><tr class="odd"><td>oprationGridVoltageV</td><td>DC/DC Converterに設定するDC Grid動作電圧 (V)</td></tr><tr class="even"><td>operationGridVoltageVRange.min</td><td>CV Mode時Local Safety Check : DC Grid最小動作電圧 (V)</td></tr><tr class="odd"><td>operationGridVoltageVRange.max</td><td>CV Mode時Local Safety Check : DC Grid最大動作電圧 (V)</td></tr><tr class="even"><td>gridVoltageSeparationV</td><td>電圧権限獲得動作の電圧Step (V)</td></tr><tr class="odd"><td>gridVoltageAllowanceV</td><td>DC Gird電圧の許容誤差 (V)</td></tr><tr class="even"><td>gridVoltageDropAllowanceV</td><td>Gridの最大インピーダンスと最大電力融通電流による電圧Dropの最大値(V)</td></tr><tr class="odd"><td>gridCurrentStepA</td><td>DC Gridの電流Step(A)</td></tr></tbody></table>

<table><thead><tr class="header"><th>gridUvloMaskV</th><th>DC/DC Converter EZA2500を使用する際に用いる。EZA2500はDC Grid電圧が最小動作電圧付近にある場合、CV Modeで起動できないという制限がある。そこでapis-main側で　operationGridVoltageRange.min±gridUvloMaskVの電圧範囲ではCV Modeで起動しないように制御している。</th></tr></thead><tbody><tr class="odd"><td><p>safety.sumOfDealingUnitGrid</p><p>CurrentAllownacePerUnitA</p></td><td>Global Safety Check : 1ノード毎のDC Grid電流誤差 (A) (N台のノードが電力融通に参加していた場合は N倍がDC Grid電流誤差となる。)</td></tr><tr class="even"><td>safety.sumOfDealGridCurrentMaxA</td><td>Global Safety Check : DC Grid上の最大電流 (A)</td></tr><tr class="odd"><td><p>safety.gridTopologyBasedEvaluation</p><p>.enabled</p></td><td>DC Grid配線トポロジーの各ブランチの最大電流容量Checkの有効/無効設定</td></tr><tr class="even"><td><p>safety.gridTopologyBasedEvaluation</p><p>.branchIds</p></td><td>DC Grid配線トポロジーの各ブランチへの割振り　(割り振られたIDは後の設定で使用する。)</td></tr><tr class="odd"><td><p>safety.gridTopologyBasedEvaluation</p><p>.branchAssociation.branchIds</p><p>.forwardUnitIds</p></td><td>各ブランチの支流の前方向に存在するノード列挙</td></tr><tr class="even"><td><p>safety.gridTopologyBasedEvaluation</p><p>.branchAssociation.branchIds</p><p>.backwardUnitIds</p></td><td>各ブランチの支流の後方向に存在するノード列挙</td></tr><tr class="odd"><td>safety.branchCurrentCapacityA</td><td>ブランチ毎の電流容量 (A)</td></tr><tr class="even"><td>heloPeriodMsec</td><td>自身のIDが重複するのを防ぐためにコミュニケーションラインに自身のIDでHelloを送る間隔 (ms)</td></tr><tr class="odd"><td><p>controller.dcdc.checkpoint</p><p>.retryLimit</p></td><td>DC/DC Converterの電圧Checkを行う際のリトライ回数</td></tr><tr class="even"><td><p>controller.dcdc.checkpoint</p><p>.retryWaitMsec</p></td><td>DC/DC Converterの電圧Checkを行う際のリトライ間隔 (ms)</td></tr><tr class="odd"><td><p>controller.dcdc.voltageReference</p><p>.rampUp.first.timeoutMsec</p></td><td>DC Grid 電圧Ramp UpのTimeout時間 (ms)</td></tr><tr class="even"><td><p>controller.dcdc.voltageReference</p><p>.authorization.numberOfTraials</p></td><td><p>DC Grid電圧 Ramp Up後の電圧Reference 権限獲得動作の電圧変更回数</p><p>(7.3電圧Reference権限獲得動作参照)</p></td></tr><tr class="odd"><td><p>controller.scramVoltageReference</p><p>DelayMsec</p></td><td>電力融通中に異常が発生した場合には全電力融通処理を止めることがあり、先にCC ModeのDC/DC ConverterをWait Modeに設定した後でCV　ModeのDC/DC ConverterをWait Modeに設定する。この項目はCC ModeをWait ModeにしてからCV ModeをWait Modeにするまでの待ち時間を設定する。 (ms)</td></tr><tr class="even"><td>controller.stopMeTimeoutMsec</td><td>Grid Masterに自身のDC/DC ConverterのWaitを依頼後、この設定時間待ってもWaitしない場合は自身でDC/DC ConverterをWaitさせる。(ms)</td></tr><tr class="odd"><td>controller.stopMeCheckPeriodMsec</td><td>Error処理で自身のDC/DC ConverterをWaitする際に定期的に電力融通の有無を確認し、Grid MasterにWaitを依頼する間隔 (ms)</td></tr><tr class="even"><td><p>controller.dataAcquisition</p><p>PeriodMsec</p></td><td>自身のDC/DC ConverterやBatteryから情報を取得する間隔 (ms)</td></tr><tr class="odd"><td>controller.retryLimit</td><td>自身のDC/DC ConverterやBatteryから情報を取得する際に失敗した場合にRetryする回数</td></tr><tr class="even"><td>user.errorHandlingPeriodMsec</td><td>Local Error処理を終了してから次のLocal Error処理を開始するまでの待ち時間 (ms)</td></tr><tr class="odd"><td>user.errorSustainingMsec</td><td>Error処理後から電力融通RequestやAcceptを開始するまでの待ち時間 (ms)</td></tr><tr class="even"><td>user.houseKeepingPeriodMsec</td><td>Battery残容量とScenarioファイルを確認してから次の確認を開始するまでの待ち時間 (ms)</td></tr><tr class="odd"><td>mediator.deal.gridCurrentA</td><td>1電力融通の電流 (A)</td></tr><tr class="even"><td>mediator.deal.amountMinWh</td><td>1電力融通の最小電力量 (Wh)</td></tr><tr class="odd"><td>mediator.deal.amountMaxWh</td><td>1電力融通の最大電力量 (Wh)</td></tr><tr class="even"><td>mediator.deal.amountUnitWh</td><td>1電力融通の電力量の単位 (Wh)</td></tr><tr class="odd"><td>mediator.negotiationTimeoutMsec</td><td>他のapis-mainにRequestを発行した後に待つ時間(ms)</td></tr><tr class="even"><td>mediator.dealLoggingPeriodMsec</td><td>電力融通中のLogの書き込み完了から次のLogの書き込み開始までの待ち時間 (ms)</td></tr><tr class="odd"><td>gridMaster.voltageReferenceSide</td><td><p>電圧Referenceが放電側か充電側か決定する方法</p><p>charge : 放電側</p><p>discharge : 充電側　</p></td></tr><tr class="even"><td><p>gridMaster.voltageReference</p><p>TakeOverDvg</p></td><td><p>電圧Reference移動時の移動先電圧を決定する方法</p><p>　theoretical : 移動元DC Grid電圧使用</p><p>上記以外 : 移動先DC Grid電圧使用</p></td></tr><tr class="odd"><td><p>gridMaster.masterDealSelection</p><p>.strategy</p></td><td><p>電圧Referenceを含んだ電力融通が終了するときに次のmaster dealを選択する方法</p><p>　newestDeal : 最新電力融通を選択</p><p>　それ以外 : Error</p></td></tr><tr class="even"><td><p>gridMaster.gridMasterSelection</p><p>.strategy</p></td><td><p>Grid Master選択方法 (4.2 Grid Master選定参照)</p><p>　　anywhere</p><p>fixed</p><p>voltageReference</p></td></tr><tr class="odd"><td><p>gridMaster.gridMasterEnsuring</p><p>.interlockinconsistency.retryWaitMsec</p></td><td>Grid Master不在検知後の起動でGrid Masterのインタロックの不整合を検知した時の再試行までの待ち時間 (ms)</td></tr><tr class="even"><td><p>gridMaster.gridMasterWatching</p><p>.absence.ensureWaitMsecc</p></td><td>Grid Master不在検知後に再確認するまでの待ち時間 (ms)</td></tr><tr class="odd"><td><p>gridMaster.deal.forceStopCondition</p><p>.dischargeUnitLowerLimitRsoc</p></td><td>電力融通を強制的に停止するBattery残容量の下限 (%)</td></tr><tr class="even"><td><p>gridMaster.deal.forceStopCondition</p><p>.chargeUnitUpperLimitRsoc</p></td><td>電力融通を強制的に停止するBattery残容量の上限 (%)</td></tr><tr class="odd"><td>gridMaster.deal.referenceSide</td><td><p>融通電力算出する際に放電側をReferenceにするか、充電側をReferenceにするかを決定する方法</p><p>　chargeノード : 充電側</p><p>dischargeノード : 放電側</p></td></tr><tr class="even"><td>gridMaster.deal.resetLimit</td><td>電力融通情報ごとの電圧Reference権限獲得失敗回数の上限</td></tr><tr class="odd"><td><p>gridMaster.currentCompensation</p><p>.limitOfTraials</p></td><td><p>電流補正の回数の上限</p><p>(7.4 電流補正機能参照)</p></td></tr><tr class="even"><td><p>gridMaster.currentCompensation</p><p>.driftAllowanceA</p></td><td><p>電流補正後の許容誤差 (A)</p><p>(7.4 電流補正機能参照)</p></td></tr><tr class="odd"><td><p>gridMaster.gridVoltageOptimization</p><p>.enabled</p></td><td>Grid電圧最適化処理有効/無効設定</td></tr><tr class="even"><td>gridMaster.heloPeriodMsec</td><td>Grid Masterの重複起動を防ぐためにEvent BusにHelloを送る間隔 (ms)</td></tr><tr class="odd"><td>gridMaster.dataCollectionPeriodMsec</td><td>Grid Masterが全ノードからの情報収集間隔 (ms)</td></tr><tr class="even"><td>gridMaster.dataCollectionTimeoutMsec</td><td>Grid Master情報収集Timeout時間 (ms)</td></tr><tr class="odd"><td>gridMaster.mainLoopPeriodMsec</td><td>Grid MasterのMain Loop処理が終了し、次のMain Loop処理を開始するまでの待ち時間 (ms)</td></tr><tr class="even"><td>gridMastererrorSustainingMsec</td><td>Grid MasterがGlobal Error処理を終了してから次のGlobal Error処理を開始するまでの待ち時間 (ms)</td></tr></tbody></table>

**cluster.xml**
---------------

xml形式のファイルでHazelcastがクラスタを構築する際に必要なパラメータ(クラスタ名称、パスワード、ネットワーク設定、マルチキャスト設定など)を設定する。

暗号化しcluster.xml.encrypted として保存される。

**logging.properties**
----------------------

Javaの標準APIであるjava.util.loggingのLogの出力に関する設定(Logファイルの保存先、Log の保存容量、Logレベルの設定など)が記述されているファイル。

**start.sh**
------------

apis-mainを起動させるスクリプトファイル。OS起動時の自動実行で実行される。

以下にstart.sh内でのapis-mainを起動させるコマンドを示す。

java -XX:OnOutOfMemoryError="'kill -KILL %p'" -Duser.timezone=Asia/Tokyo -Djava.util.logging.config.file=./logging.properties -jar ./apis-main-2.xx.x-a01-fat.jar -conf ./config.json -cp ./ -cluster -cluster-host 192.168.0.1 &

“java”の後の引き数の意味を以下に説明する。

-XX:OnOutOfMemoryError="'kill -KILL %p'"　

-&gt; メモリ不足(OOM Error)発生時プロセスをKillするオプション。

-Duser.timezone=Asia/Tokyo

　-&gt; Timezone設定。

-Djava.util.logging.config.file=./logging.properties

　-&gt;Log構成ファイルを指定するオプション。

-jar ./apis-main-2.xx.x-a01-fat.jar

-&gt;JARファイルの中にカプセル化されたプログラムの実行を指定するオプション。

-conf ./config.json

　-&gt;構成ファイルを指定するオプション。

-cp ./

　-&gt;cluseter.xmlファイルの位置を指定するオプション。

-cluster-host 192.168.0.1 &

-&gt;自身のIP Addressを指定するオプション。

**stop-kill.sh**
----------------

apis-mainを停止させるスクリプトファイル。

Event Bus経由のShutdown機能(stop)を実施した後、それがタイムアウトした場合に自身の

Javaプロセスを強制終了させる処理を行う。スクリプトの中でタイムアウトを秒で指定する

ことが可能である。

**key.pem**
-----------

Event BusのSSL化に使われる秘密鍵。

**cert.pem**
------------

Event BusのSSL化に使われる証明書。

1.  **DC Grid制御機能**
    ===================

    1.  **電圧Referenceの選定**
        -----------------------

ノード間の交渉が成立し電力融通を開始する際に、DC Grid上に電圧Referenceが存在しない場合には、Policyファイルに記載されたパラメータ(gridMaster.voltageRefereceSide)を元に電圧Referenceのノードを決定する。電圧Referenceを充電側にするか放電側にするか選択可能だが放電側を選択する場合には、使用する蓄電システム仕様や融通条件などの検証が必要である。

**電圧Ramp Up**
---------------

Grid Masterは電圧ReferenceとなるノードのDC/DC ConverterのModeをWait ModeからCV Modeに変更し、Policyファイル内に記載されたターゲット電圧値(oprationGridVoltageV)になるまでDC Grid電圧をRamp Upさせる。ただし、電圧Ramp Up前にDC Grid電圧がPolicyファイルに規定された電圧( operationGridVoltageVRange.min+gridUvloMaskV+10V)以上である場合には、他に電圧源が存在する可能性があるためハードウェアエラーを発生させ電圧Ramp Upを停止させる。DC Grid電圧がPolicyファイルに規定された電圧領域(operationGridVoltageVRange.min±gridUvloMaskV)である場合には、DC/DC Converterの仕様で起動できない電圧領域であるため、電圧Reference ノードのDC/DC ConverterのModeをWait状態とする。DC Grid電圧がPolicyファイル内に規定された時間内(controller.dcdc.voltageReference.rampUp.first.timeoutMsec)にターゲット電圧値まで上がったら電圧Ramp upは完了するが、時間内にターゲット電圧に達しない場合には電圧Ramp Upを停止させる。

**電圧Reference権限獲得動作**
-----------------------------

電圧Ramp Up完了後、電圧Referenceの ノードはDC Gridの電圧をランダムに変化させる。これを電圧Reference権限獲得動作と呼ぶ。DC Grid電圧をランダムに変化させた際に、一度でもターゲット電圧に追従できない、もしくは異常電流が検知される場合には電圧Referenceの競合が発生したとして、該当するDC/DC Converterのターゲット電圧をPolicyファイルに規定されたDC Grid停止電圧( operationGridVoltageVRange.min+gridUvloMaskV)に設定後、待機状態にして、一定時間Waitした後、再度電圧Ramp up、電圧Reference権獲得動作を実施する。この一連の動作を設定回繰り返しても電圧Reference権獲得動作が終了しない場合には、電圧Referenceの競合が発生したとして、該当する電力融通を停止する。

**電流補正機能**
----------------

電力融通の電流値は理想的には放電側と充電側は同じ値になるべきだが、各 ノードのDC/DC Converter搭載の電流計にはセンサ誤差があるため、例えば放電側のセンサが2Aと表示しても充電側のセンサでは1.9Aの表示になることがある。そこで各ノードの誤差を電圧Referenceが吸収してしまうのを防ぐためReference側ではない方のノードの電流を調整してReference側の電流を期待値に補正する。

・図7-1の右図のようにReferenceがCV側(電圧Reference側)の場合はCC側の電流値を

調整しCV側の電流値が期待された電力融通電流になるように補正を行う。

・図7-1の左図のようにReferenceがCC側の場合は自ら設定した値が期待値になるため

特に補正は行わない。

・二つ目以降の電力融通の場合は二つ目の電力融通を立ち上げた後にReferenceではない方のCCの 電流を調整しCVの電流を電力融通立ち上げ前の電流に戻すように補正する。

<img src="media/media/image17.png" style="width:5.90278in;height:2.57431in" />
<p align="center">図7-1</p>

**Constant Voltage(CV)移動**
----------------------------

Constant Voltage(CV)の移動は以下のようにDroop制御によって行われる。

・図7-2は２つの電力融通が行われている様子を示している。

<img src="media/media/image18.png" style="width:2.73028in;height:1.33913in" />
<p align="center">図7-2</p>

・図7-3はCVを含んだ電力融通が終了した様子を示している。

<img src="media/media/image19.png" style="width:2.88064in;height:1.4in" />
<p align="center">図7-3</p>

・図7-4は旧CVにDroop設定を行い、さらに新CVもDroop設定を行って起動させる様子を示している。

<img src="media/media/image20.png" style="width:2.77391in;height:1.51797in" />
<p align="center">図7-4</p>


・図7-5は新CVが起動したら旧CVのDC/DC ConverterはWait Modeにして新CVのDroop率を0に設定する様子を示している。

<img src="media/media/image21.png" style="width:2.74783in;height:1.50966in" />
<p align="center">図7-5</p>


**電圧最適化**
--------------

電力融通の効率最大化を目的としてDC/DC Converterを最大効率点で動作させるために以下の流れでDC Grid電圧の最適化を行う。(この機能はpolicy.json のgridVoltageOptimizationでON/OFF可能である。)

1.  DC/DC Converterの効率が最大となるBattery電圧とDC Grid電圧の入出力電圧比(efficientBatteryGridvaltageRatio)をhwConfig.jsonに保持する。

2.  電力融通を実施する各ノードに対して、最適なDC Grid電圧を下記にて算出する。

> Battery電圧(VBAT) x efficientBatteryGridvaltageRatio

1.  全ノードの最適DC Grid電圧の平均値をターゲットのDC Grid電圧(VGRID)とする。

2.  DC Grid電圧の範囲を以下の式で求める。

Vmin = operationGridVoltageVRange.min + gridVoltageDropAllownaceV

Vmax = operationGridVoltageVRange.max – gridVoltageDropAllownaceV

> (operationGridVoltageVRange.min/max, gridVoltageDropAllowanceVはpolicy.jsonに記載。)

1.  (4)で算出されたVmin～Vmaxが330～370Vの範囲外である場合はその範囲に収まるようにDC Gridの電圧範囲を調整する。

2.  (3)で算出されたVGRIDが(5)の電圧範囲に収まることを確認する。範囲に収まればVGRIDをDC Grid電圧に設定し、VGRID &lt; Vminの場合はVminを、 VGRID&gt; VmaxのVmaxをDC Grid電圧として設定する。

3.  新たな電力融通を起きる際、電力融通が終了する際に上記計算を実施して、DC Grid電圧を調整する。

**Grid Master Data収集**
========================

Grid Masterは以下の情報をPolicyファイルで設定された間隔で全ノードから収集している。

Grid Masterが収集した情報を不揮発性メモリに記録することはない。

<table><thead><tr class="header"><th>apis</th><th>version</th><th>apis-main version</th></tr></thead><tbody><tr class="odd"><td></td><td>remaining_capacity_wh</td><td>Battery残容量(Wh)</td></tr><tr class="even"><td></td><td>deal_interlock_capacity</td><td>1融通 1スロットとした場合に、同時に融通可能なスロット数</td></tr><tr class="odd"><td></td><td>operation_mode.global</td><td><p>クラスタ全体のOperation Mode設定</p><p>autonomous : 通常の電力融通Mode</p><p>heteronomous : 既存電力融通継続</p><p>新電力融通生成無効</p><p>stop : 電力融通停止Mode</p><p>manual : 手動Mode (評価用)</p></td></tr><tr class="even"><td></td><td>operation_mode.local</td><td><p>自ノードのOperation Mode設定</p><p>空 : operation_mode.global</p><p>に従う</p><p>heteronomous : 既存電力融通継続</p><p>新電力融通生成無効</p><p>stop : 電力融通停止Mode</p></td></tr><tr class="odd"><td></td><td>operation_mode.effective</td><td><p>有効Operation Mode</p><p>globalとlocalのOperation Modeの組み合わせにて決定</p></td></tr><tr class="even"><td>oesunit</td><td>communityId</td><td>コミュニティID</td></tr><tr class="odd"><td></td><td>clusterId</td><td>クラスタID</td></tr><tr class="even"><td></td><td>id</td><td>ノードID</td></tr><tr class="odd"><td></td><td>display</td><td>ノード名称</td></tr><tr class="even"><td></td><td>sn</td><td>ノードシリアルNo.</td></tr><tr class="odd"><td></td><td>budo</td><td><p>旧システムでは自動融通がActiveになっていることを示すフラグだったが、</p><p>現行システムではoperation_mode.effective</p><p>がautonomousかそれ以外かを示すフラグとなっている。</p><p>autonomous : 1</p><p>それ以外 : 0</p></td></tr><tr class="even"><td></td><td>ip</td><td>IPv4</td></tr><tr class="odd"><td></td><td>Ipv6_ll</td><td>IPv6リンクローカルユニキャスト</td></tr><tr class="even"><td></td><td>Ipv6_g</td><td>IPv6グローバルユニキャスト</td></tr><tr class="odd"><td></td><td>mac</td><td>MAC address</td></tr><tr class="even"><td>battery</td><td>rsoc</td><td>相対残容量 (%)</td></tr><tr class="odd"><td></td><td>battery_operation_status</td><td>電力融通許可/不許可フラグ</td></tr><tr class="even"><td>time</td><td>apis-mainノードの時間</td><td></td></tr><tr class="odd"><td>dcdc</td><td>status.status</td><td><p>状態</p><p>・停止モード ：0x0000</p><p>・CVモード ：0x0014</p><p>・CCモード(放電)：0x0041</p><p>・CCモード(放電)：0x0002</p><p>APISからのMode指示はこちらの値を使用して制御される</p></td></tr><tr class="even"><td></td><td>status.alarm</td><td><p>Alarm番号</p><p>制御には使われないため必須ではない。</p></td></tr><tr class="odd"><td></td><td>status.stateAlarm</td><td>Alarm情報</td></tr><tr class="even"><td></td><td>status.statusName</td><td><p>DC/DC Converter Status名称</p><p>制御には使われないため必須ではない。</p></td></tr><tr class="odd"><td></td><td>status.runningState</td><td><p>DC/DC Converter動作 Status</p><p>制御には使われないため必須ではない。</p></td></tr><tr class="even"><td></td><td>status.operationMode</td><td><p>Operation Mode</p><p>・停止モード ："Waiting"</p><p>・CVモード ："Grid Autonomy"</p><p>・CCモード(放電)："Heteronomy CV"</p><p>・CCモード(放電)："Heteronomy CV"</p></td></tr><tr class="odd"><td></td><td>meter.wg</td><td>DC Grid 電力 (W)</td></tr><tr class="even"><td></td><td>meter.vg</td><td>DC Grid電圧 (V)</td></tr><tr class="odd"><td></td><td>meter.ig</td><td>DC Grid電流 (A)</td></tr><tr class="even"><td></td><td>meter.wb</td><td>Battery電力 (W)</td></tr><tr class="odd"><td></td><td>meter.vb</td><td>Battery電圧 (V)</td></tr><tr class="even"><td></td><td>meter.ib</td><td>Battery電流 (A)</td></tr><tr class="odd"><td></td><td>meter.tmp</td><td>内部温度 (℃)</td></tr><tr class="even"><td></td><td>vdis.dvg</td><td>DC Grid目標電圧値 (V)</td></tr><tr class="odd"><td></td><td>vdis.drg</td><td>DC Grid Droop率 (%)</td></tr><tr class="even"><td></td><td>param.dig</td><td><p>DC Grid上限電流 (A)</p><p>電流は向きに関わらず、絶対値で指示される</p></td></tr><tr class="odd"><td></td><td>param.ogv</td><td><p>DC Grid過電圧閾値 (V)</p><p>制御には使われないため必須ではない。</p></td></tr><tr class="even"><td></td><td>param.ugv</td><td><p>DC Grid低電圧閾値 (V)</p><p>制御には使われないため必須ではない。</p></td></tr><tr class="odd"><td></td><td>param.cib</td><td><p>Battery上限電流 (A)</p><p>制御には使われないため必須ではない。</p></td></tr><tr class="even"><td></td><td>param.obv</td><td><p>Battery過電圧閾値 (V)</p><p>制御には使われないため必須ではない。</p></td></tr><tr class="odd"><td></td><td>param.ubv</td><td><p>Battery低電圧閾値 (V)</p><p>制御には使われないため必須ではない。</p></td></tr></tbody></table>

1.  **Log取得・保存機能**
    =====================

    1.  **apis-main動作Log**
        --------------------

Log出力にはJava標準APIのjava.util.loggingを使っており以下の7つのLevelに分類されている。apis-mainとしては”CONFIG”, “FINER”のLevelは使用しない。これらのapis-mainの動作Logはlogging.propertiesファイルに記載することでLogファイルの保存先、保存するLog Level、最大Logサイズ、最大保存Log数などの設定を行っている。

\[java.util.logging Log Level\]

1.SEVERE

→致命的な状況やErrorについての情報で、問題が発生し処理が継続不能な状況である。

apis-main処理の”FATAL”, “ERROR”に対応する。

2.WARNING

→警告についての情報で、問題が発生しているが処理は継続可能な状況である。

apis-main処理の”WARN”の情報に対応する。

3\. INFO

→正常系の情報で、特に動作として重要なイベントを行った場合に出力する。

apis-main処理の”INFO”に対応する。

4.CONFIG

　　→設定に関する情報である。

apis-mainとしてはこのLevelのLog出力はない。

5.FINE　

→デバッグ情報である。apis-main処理の”DEBUG”に対応する。

6.FINER

　　→特定の処理についての開始及び終了の情報。内部に発生した例外に関する情報である。

　　　apis-mainとしてこのLevelのLog出力はない。

7.FINEST

→トレース情報である。apis-main処理の”TRACE”に対応する。

**apis-main動作Log出力先**
--------------------------

apis-mainの動作LogはUDP、Console、ファイルの3つの出力先がある。logging.propertiesの設定でそれぞれの出力の有無や前頁で述べた出力Levelの制限をかけることができる。UDPはCommunication Lineに出力されるため情報漏洩や通信のトラフィックを考慮して設定し、ファイルへの出力は不揮発性メモリの容量を考慮して設定する。

<img src="media/media/image22.png" style="width:4.71779in;height:3.28205in" />
<p align="center">図9-1</p>

**電力融通Log**
---------------

電力融通を行った両側のノードに同じ内容の情報が電力融通Logとして保存される。

保存される内容は以下の項目で、保存先はConfigファイルにて指定する。

<table><thead><tr class="header"><th>unitId</th><th>ノード識別ID</th></tr></thead><tbody><tr class="odd"><td>negotiationId</td><td>電力融通交渉ID</td></tr><tr class="even"><td>requestUnitId</td><td>電力融通をRequestしたノードID</td></tr><tr class="odd"><td>acceptUnitId</td><td>電力融通をAcceptしたノードID</td></tr><tr class="even"><td>requestDateTime</td><td>電力融通をRequestした日時</td></tr><tr class="odd"><td>acceptDateTime</td><td>電力融通をAcceptした日時</td></tr><tr class="even"><td>requestPointPerWh</td><td>Request側が提示した1Wh当たりのポイント</td></tr><tr class="odd"><td>acceptPontPerWh</td><td>Accept側が提示した1Wh当たりのポイント</td></tr><tr class="even"><td>requestDealGridCurrentA</td><td>Request側が提示した融通の電流値</td></tr><tr class="odd"><td>acceptDealGridCurrentA</td><td>Accept側が提示した融通の電流値</td></tr><tr class="even"><td>type</td><td>電力融通Requestのタイプ(充電/放電)</td></tr><tr class="odd"><td>chargeUnitId</td><td>充電側のノードID</td></tr><tr class="even"><td>dischargeUnitId</td><td>放電側のノードID</td></tr><tr class="odd"><td>pointPerWh</td><td>実際の電力融通時の1Wh当たりのポイント</td></tr><tr class="even"><td>chargeUnitEfficientGridVoltageV</td><td>充電側ノードの効率が良いGrid電圧</td></tr><tr class="odd"><td>dischargeUnitEfficientGridVoltageV</td><td>放電側ノードの効率が良いGrid電圧</td></tr><tr class="even"><td>dealGridCurrentA</td><td>電力融通時電流値(A)</td></tr><tr class="odd"><td>requestAmountWh</td><td>Request側が提示した電力量</td></tr><tr class="even"><td>acceptAmountWh</td><td>Accept側が提示した電力量</td></tr><tr class="odd"><td>dealAmountWh</td><td>電力融通時電力量(Wh)</td></tr><tr class="even"><td>dealId</td><td>電力融通情報に付与されたID</td></tr><tr class="odd"><td>createDateTime</td><td>電力融通の電力融通情報が作られた日時</td></tr><tr class="even"><td><p>compensationTargetVoltage</p><p>ReferenceGridCurrentA</p></td><td>電圧Referenceを担っているノードの電流補正のターゲット値 (A)</td></tr><tr class="odd"><td>activateDateTime</td><td>Constant Voltageノード側の起動を開始した日時</td></tr><tr class="even"><td>rampUpDateTime</td><td>DC Gridの電圧Ramp Upが完了した日時</td></tr><tr class="odd"><td>warmUpDateTime</td><td>Constant Currentノード側を起動した日時</td></tr><tr class="even"><td><p>dischargeUnitCompensated</p><p>GridCurrentA</p></td><td>電流補正後の放電電流 (A)</td></tr><tr class="odd"><td><p>chargeUnitCompensated</p><p>GridCurrentA</p></td><td>電流補正後の充電電流 (A)</td></tr><tr class="even"><td>startDateTime</td><td>実際の電力融通を開始した日時</td></tr><tr class="odd"><td>cumulateDateTime</td><td>実際に電力融通した電力を積算した日時</td></tr><tr class="even"><td>cumulateAmountWh</td><td>実際に電力融通した総電力量 (Wh)</td></tr><tr class="odd"><td>stopDateTime</td><td>実際の電力融通を停止した日時</td></tr><tr class="even"><td>deactiveateDateTime</td><td>電力融通後の処理が完了した日時</td></tr></tbody></table>

1.  **Error処理・保護機能**
    =======================

    1.  **apis-main Error処理**
        -----------------------

apis-mainのError処理は大きく分けてHardware, Framework, Logic, Userの4つのカテゴリがある。それぞれのカテゴリの中にはクラスタ全体に影響があるGlobalと、不具合が発生したノードだけに影響があるLocalがあり、Globalの場合はGrid MasterがError処理を行い、Localの場合は各apis-mainがError処理を行う。

1.  Hardware

> DC/DC ConverterやBatteryなどのハードウェアに不具合が発生し、DC Grid全体に影響を及ぼす場合には実行中の全電力融通を停止し、不具合が発生したノードだけに影響がある場合には該当 ノードの電力融通を停止させる。DC Grid全体に影響を及ぼす場合はHARDWARE:GLOBAL:XXXX、不具合が発生したノードだけに影響がある場合はHARDWARE:LOCAL:XXXXと分類する。不具合の状況によって実行中の電力融通を停止させるだけではなくapis-mainをShutdownさせることもあり、この場合をFATALと分類し、 Warningだけ残す場合はWARNと分類する。
>
> \[Hardware不具合具体例\]

-最初の電力融通でCV Modeを起動する際に既にDC Grid電圧が高い場合

> HARDWARE:GLOBAL:ERROR : 全電力融通停止。

-CV Modeの電圧Ramp Up時間が長すぎてTimeoutする場合

> HARDWARE:GLOBAL:ERROR : 全電力融通停止。

-電力融通中にDC Grid上の電流の充放電の合計がゼロでない場合 (どこかで漏れている)

> HARDWARE:GLOBAL:ERROR : 全電力融通停止。

-電力融通中にDC Grid上の電流値が許容量を超えている場合

> HARDWARE:GLOBAL:ERROR : 全電力融通停止。

-DC/DC ConverterやBatteryなどのハードウェアに不具合が発生した場合

> 不具合によるため異常系処理のカテゴリは一意には決まらない。

1.  Framework

> クラスタを生成するフレームワークであるVert.x, Hazelcast上の問題や、ネットワーク, ファイル Systemに不具合がありGrid全体に影響を及ぼす場合には実行中の全電力融通を停止し、不具合が発生したノードだけに影響がある場合には該当ノードの電力融通を停止させる。Grid全体に影響を及ぼす場合はFRAMEWORK:GLOBAL:XXXX、不具合が発生した ノードだけに影響がある場合はFRAMEWORK:LOCAL:XXXXと分類する。不具合の状況によって実行中の電力融通を停止させるだけではなくapis-mainをShutdownさせることもあり、この場合をFATALと分類し、 Warningだけ残す場合はWARNと分類する。
>
> \[Framework不具合具体例\]
>
> -フレームワークであるVert.x, Hazelcast上の処理でErrorが発生した場合
>
> 不具合によるため異常系処理のカテゴリは一意には決まらない。
>
> -不揮発性メモリの容量不足でLogが残せない場合
>
> FRAMEWORK:LOCAL:FATAL : 該当するapis-mainをShutdownする。

1.  Logic

> 電力融通Requestを受けたが内容が無かったリ、設定されるべきパラメータが欠けているなどのErrorが発生し、Grid全体に影響を及ぼす場合には実行中の全電力融通を停止し、不具合が発生したノードだけに影響がある場合には該当ノードの電力融通を停止させる。Grid全体に影響を及ぼす場合はLOGIC:GLOBAL:XXXX、不具合が発生したノードだけに影響がある場合はLOGIC:LOCAL:XXXXとカテゴリ分けされる。不具合の状況によって実行中の電力融通を停止させるだけではなくapis-mainをShutdownさせることもあり、この場合をFATALと分類し、 Warningだけ残す場合はWARNと分類する。
>
> \[Logic不具合具体例\]
>
> -通信パケット内にあるはずのパラメータが存在しない場合、電力融通Requestメッセージの中身が空の場合、CV Modeなのに電力融通に参加していないなどLogic的な例外Errorが発生した場合など。不具合の影響がクラスタ全体に関わるか、該当するapis-mainのみに関わるかによって異なる。

LOGIC:GLOBAL:ERROR : 全apis-main をResetする。

LOGIC:LOCAL:ERROR : 該当するapis-main をResetする。

1.  User

> Config, Policy, Scenarioファイルの不備や設定値の不備があり、Grid全体に影響を及ぼす場合には実行中の全電力融通を停止し、不具合が発生した ノードだけに影響がある場合には該当 ノードの電力融通を停止させる。Grid全体に影響を及ぼす場合はUSER:GLOBAL:XXXX、不具合が発生した ノードだけに影響がある場合はUSER:LOCAL:XXXXと分類する。不具合の状況によって実行中の電力融通を停止させるだけではなくapis-mainをShutdownさせることもあり、この場合をFATALと分類し、 Warningだけ残す場合はWARNと分類する。
>
> \[User不具合具体例\]
>
> -同一IDのノードが見つかった場合

USER:LOCAL:FATAL : 該当するapis-main をShutdownする。

・apis-mainのError処理における電力融通停止はDCDC Converter視点では自身のModeをWaitに設定するという点では通常処理と同じ状態である。apis-main視点では通常処理の電力融通停止の場合はネゴシエーションによって決められた電力量の電力融通完了後DC/DC Converterを制御して電力融通を停止し、電力融通量等をLogとして記録するが、Error処理の場合は電力融通が未完了であっても強制的に電力融通を終了させ不具合の内容をLogとして記録する。異常系処理は不具合の内容によって対応が変わり、例えばapis-main Resetの場合はapis-mainをResetした後、再度電力融通に参加することができるが、apis-main Shutdownの場合はapis-main自身をShutdown させるためクラスタからも離脱し以後の電力融通には参加できない状態となる。(apis-main Shutdownはapis-mainアプリケーションのShutdownであり、OS のShutdownではない。)

・apis-main Resetはapis-mainが個々に持つLocalメモリや、共有メモリに保存されている電力融通情報や、各種インタロックや排他ロックなどの情報を初期状態に設定するのみである。

・apis-main Resetで復帰しない場合は状況によっては同じ不具合が発生し、apis-main Reset処理が繰り返されることがある。(例えばHardware不良など常に同じ不具合が発生する場合等)

分類毎にその後の処理が異なり、それぞれ以下の処理を行う。

| HARDWARE  | GLOBAL | FATAL | 実行中の全電力融通停止 → 全apis-main Shutdown |
|-----------|--------|-------|-----------------------------------------------|
|           |        | ERROR | 実行中の全電力融通停止 → 電力融通停止         |
|           |        | WARN  | Warning Log出力                               |
|           | LOCAL  | FATAL | 該当電力融通停止 → 該当apis-main Shutdown     |
|           |        | ERROR | 該当電力融通停止 → 一定期間電力融通停止       |
|           |        | WARN  | Warning Log出力                               |
| FRAMEWORK | GLOBAL | FATAL | 実行中の全電力融通停止 → 全apis-main Shutdown |
|           |        | ERROR | 実行中の全電力融通停止 → 全apis-main Reset    |
|           |        | WARN  | Warning Log出力                               |
|           | LOCAL  | FATAL | 該当電力融通停止 → 該当apis-main Shutdown     |
|           |        | ERROR | 該当電力融通停止 → 該当apis-main Reset        |
|           |        | WARN  | Warning Log出力                               |
| LOGIC     | GLOBAL | FATAL | 全電力融通停止 → 全apis-main Shutdown         |
|           |        | ERROR | 実行中の全電力融通停止 → 全apis-main Reset    |
|           |        | WARN  | Warning Log出力                               |
|           | LOCAL  | FATAL | 該当電力融通停止 → 該当apis-main Shutdown     |
|           |        | ERROR | 該当電力融通停止 → 該当apis-main Reset        |
|           |        | WARN  | Warning Log出力                               |
| USER      | GLOBAL | FATAL | 全電力融通停止 → 全apis-main Shutdown         |
|           |        | ERROR | 全電力融通停止                                |
|           |        | WARN  | Warning Log出力                               |
|           | LOCAL  | FATAL | 該当電力融通停止 → 該当apis-main Shutdown     |
|           |        | ERROR | 該当電力融通停止                              |
|           |        | WARN  | Warning Log出力                               |

1.  **その他の機能**
    ================

    1.  **トポロジー検出**
        ------------------

DC Gridに流れる電流を随時管理することにより、許容電流以上の電流を流せない仕組みがあり以下にその方法を示す。

1.  DC Gridの各配線の電流値の計算式と電流許容値をpolicy.jsonに保持する。

2.  Grid Masterは電力融通発生時に(1)の計算式を使用して各配線の電流値を計算する。

3.  (2)の電流値が配線の電流許容値を超える場合には新しい電力融通を開始しない。

4.  新しい電力融通が発生しなくてもSafety Check(12.安全確認機能で説明)として定期的に既存の電力融通電流が各配線の電流許容値を超えていない事を確認する。

以下の図はA8 -&gt; A1への電力融通が終了すると、逆向きの電流による相殺が消えてしまうためA4-A5配線の電流値が6Aとなり許容電流値の5Aを超えてしまうケースを示したものである。

<img src="media/media/image23.png" style="width:4.48466in;height:2.68056in" />
<p align="center">図11-1</p>

apis-mainは各ノードが非同期で電力融通を行うため上記のような問題が発生する可能性があるため以下の計算と比較を行って各配線の許容電流値以上を流せないようにしている。

1.  Chargeノード視点の配線電流の計算

> 現在電力融通を行っているChargeノードの電流のみに焦点を当て、ある支流方向とその逆の支流方向それぞれに対して配線毎に電流値を合算して配線の電流として考えられる最大値を求める。

1.  Dischargeノード視点の配線電流の計算

> 現在電力融通を行っているDischargeノードの電流のみに焦点を当て、ある支流方向とその逆の支流方向それぞれに対して配線毎に電流値を合算して配線の電流として考えられる最大値を求める。

1.  配線許容電流値との比較

> 1), 2)で計算した電流値をpolicy.jsonに保存してあるそれぞれの配線の許容電流値と比較し、一つでも許容値を超える配線が存在する場合には新しい電力融通を発生させない。

例として以下のDC Gridのトポロジーで計算方法について説明する。

<img src="media/media/image24.png" style="width:5.89583in;height:1.74236in" />
<p align="center">図11-2</p>

支流A-1を以下とする。

<img src="media/media/image25.png" style="width:5.90208in;height:2.15347in" />
<p align="center">図11-3</p>

支流A-1の電力融通電流計算式は以下のようになる。

(F021 F022) = F022

(F020 F021) = F022 + F021

(F019 F020) = F022 + F021 + F020

(F019 F018) = F022 + F021 + F020 + F019

(F026 F018) = F022 + F021 + F020 + F019 + F018

(F012 F026) = F022 + F021 + F020 + F019 + F018 + F026


支流A-2を以下とする。
<img src="media/media/image26.png" style="width:5.90208in;height:1.80972in" />
<p align="center">図11-4</p>

支流A-2の電力融通電流計算式は以下のようになる。

(F022 F021) = (F012 F013) + (F012 F005) + F012 + F026 + F018 + F019 + F020 + F021

(F021 F020) = (F012 F013) + (F012 F005) + F012 + F026 + F018 + F019 + F020

(F020 F019) = (F012 F013) + (F012 F005) + F012 + F026 + F018 + F019

(F019 F018) = (F012 F013) + (F012 F005) + F012 + F026 + F018

(F018 F026) = (F012 F013) + (F012 F005) + F012 + F026

(F026 F012) = (F012 F013) + (F012 F005) + F012

(F012 F013) = F004 + F017 + F015 + F014 + F013

(F012 F005) = F011 + F010 + F009 + F008 + F007 + F006 + F005

支流B-1を以下とする。
<img src="media/media/image28.png" style="width:5.90764in;height:1.95069in" />
<p align="center">図11-5</p>

支流B-1の電力融通電流計算式は以下のようになる。

(F010 F011) = F011

(F009 F010) = F011 + F010

(F008 F009) = F011 + F010 + F009

(F007 F008) = F011 + F010 + F009 + F008

(F006 F007) = F011 + F010 + F009 + F008 + F007

(F005 F006) = F011 + F010 + F009 + F008 + F007 + F006

(F012 F005) = F011 + F010 + F009 + F008 + F007 + F006 + F005

支流B-2を以下とする。

<img src="media/media/image29.png" style="width:5.90208in;height:1.79167in" />
<p align="center">図11-6</p>


支流B-2 の電力融通電流計算式は以下のようになる。

(F011 F010) = (F012 F013) + (F012 F026) + F012 + F005 + F006 + F007 + F008 + F009 + F010

(F010 F009) = (F012 F013) + (F012 F026) + F012 + F005 + F006 + F007 + F008 + F009

(F009 F008) = (F012 F013) + (F012 F026) + F012 + F005 + F006 + F007 + F008

(F008 F007) = (F012 F013) + (F012 F026) + F012 + F005 + F006 + F007

(F007 F006) = (F012 F013) + (F012 F026) + F012 + F005 + F006

(F006 F005) = (F012 F013) + (F012 F026) + F012 + F005

(F005 F012) = (F012 F013) + (F012 F026) + F012

(F012 F013) = F013 + F014 + F015 + F004 + F017

(F012 F026) = F022 + F021 + F020 + F019 + F018 + F026

支流C-1を以下とする。
<img src="media/media/image30.png" style="width:5.90208in;height:1.87708in" />
<p align="center">図11-7</p>

支流C-1の電力融通電流計算式は以下のようになる。

(F015 F004) = F004

(F015 F017) = F017

(F014 F015) = F004 + F017 + F015

(F013 F014) = F004 + F017 + F015 + F014

(F012 F013) = F004 + F017 + F015 + F014 + F013

支流C-2を以下とする。
<img src="media/media/image31.png" style="width:5.90556in;height:1.85417in" />
<p align="center">図11-8</p>

支流C-2の電力融通電流計算式は以下のようになる。

(F004 F015) = (F012 F005) + (F012 F026) + F012 + F013 + F014 + F015 + F017

(F017 F015) = (F012 F005) + (F012 F026) + F012 + F013 + F014 + F015 + F004

(F015 F014) = (F012 F005) + (F012 F026) + F012 + F013 + F014

(F014 F013) = (F012 F005) + (F012 F026) + F012 + F013

(F013 F012) = (F012 F005) + (F012 F026) + F012

(F012 F005) = F011 + F010 + F009 + F008 + F007 + F006 + F005

(F012 F026) = F022 + F021 + F020 + F019 + F018 + F026

各配線の電流計算と比較

これまでの各支流の計算式をまとめたのが以下である。

これらの計算式に対してChargeノード、Dischargeノードそれぞれの電流値を代入しCharge ノード、Dischargeノードそれぞれに対して配線に流れる電流の最大値を求め、各配線の許容電流値と比較する。

<img src="media/media/image32.png" style="width:5.90556in;height:1.55in" />


**Gateway機能**
---------------

クラスタ間の電力融通を実現するGateway機能について説明する。Gateway機能はクラスタ間を繋ぐGatewayノードによって実現する。Gatewayノードは以下の図11-9のようにBattery　1台に対して複数のapis-mainとDevice DriverそしてDC/DC Converterを持つ構成となる。

<img src="media/media/image33.png" style="width:5.9in;height:2.59167in" />
<p align="center">図11-9</p>

ハードウェアとソフトウェアの主な構成を以下に示す。

・ハードウェア構成

-Battery … 1台

-DC/DC Converter … 接続するクラスタ数と同じ台数

・ソフトウェア構成

-apis-main … 接続するクラスタ数と同じ台数

-Device Driver … 接続するクラスタ数と同じ台数

Gatewayノード内の複数のapis-mainはそれぞれ異なるクラスタに参加し互いに独立して動作を行う。そして複数のDC/DC Converterもそれぞれに対応するapis-mainの制御に従って独立して電力融通処理を実行するが、1つのBatteryを複数のapis-mainで共有しているためBatteryの最大電流容量を超えないように制御を行う必要がありそこが通常のノードと異なる点である。Batteryの最大電流容量を超えないようにファイルシステムベースの排他ロックを用いて同時に同方向の電力融通が行えないように制御を行っている。このファイルベースの排他ロック制御のため複数のapis-mainは同一のOS上で起動させる必要がある。

ファイルベースの排他ロックはConfigファイルで指定したフォルダ内に放電用の排他ロックファイルと、充電用の排他ロックファイルの2種類のファイルを作成し排他ロックを行う。

Gatewayノード上のapis-mainが電力融通のために放電を行う場合には、最初に指定フォルダの放電用排他ロックファイルの存在を確認し、ファイルが存在する場合には放電を断念し、ファイルが存在しない場合には放電用排他ロックファイルを作成した後に放電を行う。充電の場合も同様にapis-mainが電力融通のために充電を行う場合には、最初に指定フォルダの充電用排他ロックファイルの存在を確認し、ファイルが存在する場合には充電を断念し、ファイルが存在しない場合には充電用排他ロックファイルを作成した後に放電を行う。

apis-main上でGateway機能をEnableするためにはconfigファイルの以下の

2箇所を設定する。

\[configファイル\]

"batteryCapacityManagement.enabled" : true

Battery容量管理機能を起動しGateway機能を有効にする。

"fileSystemExclusiveLockFileFormat" : "/tmp/.apis.%s.lock",

排他ロックファイルを作成する場所と名前を指定する。

1.  **安全確認機能**
    ================

    1.  **Global Safety Check**
        -----------------------

> Grid MasterはPolicyファイルのgridMaster.dataCollectionPeriodMsecで規定された間隔で全ノードDataの収集処理を行う。その後収集されたDataを元に以下の確認を行う。

・DC Grid上の全電力融通電流の絶対値の合算値

　 全電力融通電流の合算値がDC Gridの最大容量を超えていないことを確認する。　

最大容量はPolicyファイルのsumOfDealGridCurrentMaxAで規定される。

・DC Gridのブランチ電流合計値

DC Gridのトポロジーの各ブランチに流れる電流が最大容量を超えていないことの

確認を行う。

各ブランチの最大容量はPolicyファイルのgridTopologyBasedEvaluationの項目で

規定されている。

・DC Grid上の全電力融通電流の合算値

　 全電力融通電流の合算値指定値に対してPolicyファイルで規定されている範囲を超えていないことを確認する。　

> 範囲はPolicyファイルの±sumOfDealingUnitGridCurrentAllowancePerUnitA\*N (N : 電力融通に関わるノード数)で規定される。

**Local Safety Check**
----------------------

### **12.2.1.静的Local Safety Check**

　各ノードはhwConfigファイルのdataAcquisitionPeriodMsecで規定された間隔で自身Dataの収集処理を行いハードウェア性能に対する逸脱確認を行う。

　

・DC/DC Converter温度

　　DC/DC Converter温度がhwConfigファイルで規定されたスペックに収まっていることを確認する。温度の最小値はhwConfigファイルのsafety.range.DC/DC.meter. tpm.min で、最大値はsafety.range.DC/DC.meter.tpm.maxで規定されている。

・DC/DC Converter DC Grid側電圧

　　DC/DC ConverterのDC Grid側の電圧がhwConfigファイルで規定されたスペックに収まっていることを確認する。電圧の最小値はhwConfigファイルのsafety.range.　DC/DC.meter.vg.min で、最大値はsafety.range.DC/DC.meter.vg.maxで規定されている。

・DC/DC Converter DC Grid側電流

　　DC/DC ConverterのDC Grid側の電流がhwConfigファイルで規定されたスペックに収まっていることを確認する。電流の最小値はhwConfigファイルのsafety.range.　DC/DC.meter.ig.min で、最大値はsafety.range.DC/DC.meter.ig.maxで規定されている。

・DC/DC Converter Battery側電圧

　　DC/DC ConverterのBattery側の電圧がhwConfigファイルで規定されたスペックに収まっていることを確認する。電圧の最小値はhwConfigファイルのsafety.range.emu.　DC/DC.meter.vb.min で、最大値はsafety.range.emu.DC/DC.meter.vb.maxで規定されている。

・DC/DC Converter Battery側電流

　　DC/DC ConverterのBattery側の電流がhwConfigファイルで規定されたスペックに収まっていることを確認する。電流の最小値はhwConfigファイルのsafety.range.emu.　DC/DC.meter.ib.min で、最大値はsafety.range.emu.DC/DC.meter.ib.maxで規定されている。

### **12.2.2. 動的Local Safety Check**

> DC/DC ConverterやDC Gridの電圧、電流がPolicyファイルやhwConfigファイルで規定された誤差範囲に収まっていることや、最大許容電流を超えていないことなどDC/DC Converterの制御Modeに基づき正しく制御が行われていることを確認する。
>
> 各ノードはhwConfigファイルのdataAcquisitionPeriodMsecで規定された間隔で自身Dataの収集処理を行う。その後収集されたDataを元に以下の確認を行う。

・DC/DC Converter DC Grid側電流

　　DC/DC ConverterのDC Grid側の電流がhwConfigファイルで規定された許容量に収まっていることを確認する。許容量はhwConfigファイルのgridCurrentCapacityAで規定されている。

・DC/DC Converter DC Grid側電流誤差

　　DC/DC ConverterのDC Grid側の電流誤差がhwConfigファイルで規定された範囲に収まっていることを確認する。範囲はhwConfigファイルの±gridCurrent AllowanceAで規定されている。

1.  **セキュリティ**
    ================

    1.  **apis-main間通信セキュリティ**
        -------------------------------

apis-main間のやり取りはフレームワーク(Vertx, Hazelcast )がサポートするEvent Bus通信とHazelcast通信によって行われている。それぞれの通信ではセキュリティのため以下の方法で暗号化を行っている。

\(1\) Event Bus通信

-SSL公開鍵暗号方式 (RSA)

-SSL自己署名証明書

\(2\) Hazelcast通信

　 -共通鍵暗号方式(AES 128bit)

**Device Driver アクセス制限**
------------------------------

Device DriverはWeb APIでアクセスを行うが、IP AddressとPortで制限をかけることにより他のノードからのアクセスは制限され自身内からのみアクセスを可能とする。

**プライバシー**
================

GDPR等は、IP Addressとその他の情報との組み合わせで個人識別につながる場合には個人情報扱いとなり個人情報保護規制の対象になるため注意が必要である。

Localネットワーク内のみで成立するSystem構成の場合はapis-mainで取得された情報は外部サーバに送信することはないため個人情報保護規制の対象にはならないが、外部のサーバに送信する場合には注意が必要である。

**OSSライセンス**
=================

以下にapis-mainが使用するソフトウェアとそのOSSライセンスの情報を記載する。Adopt OpenJDKはライブラリのリンクのみを行っているためClasspath Exceptionが適用されGPLv2であってもapis-mainのソースコードの公開を要求されない。その他のOSSソフトウェアもapis-mainのソースコードの公開を要求するライセンスはない。

■apis-mainで使用されるソフトウェアとそのOSSライセンス情報

<table><thead><tr class="header"><th>ソフトウェア</th><th>バージョン</th><th>ライセンス</th><th>コード改変</th></tr></thead><tbody><tr class="odd"><td>Adopt OpenJDK</td><td>11.0.4+11</td><td>GPLv2 with Classpath Exception</td><td>無</td></tr><tr class="even"><td>Vert.x</td><td>3.7.1</td><td><p>デュアルライセンス(以下選択)</p><p>Eclipse Public License2.0</p><p>Apache License2.0</p></td><td>無</td></tr><tr class="odd"><td>Hazelcast</td><td>3.6.3</td><td>Apache License2.0</td><td>無</td></tr><tr class="even"><td>Netty</td><td>4.1.8</td><td>Apache License2.0</td><td>無</td></tr><tr class="odd"><td>FasterXML/Jackson</td><td>2.7.4</td><td>Apache License2.0</td><td>無</td></tr><tr class="even"><td>ReactiveX/RxJava</td><td>1.3.0</td><td>Apache License2.0</td><td>無</td></tr></tbody></table>

※諸事情によりソフトウェアバージョンは変更される可能性があります。

1.  **動作環境**
    ============

    1.  **ハードウェア要求**
        --------------------

以下にapis-mainのハードウェア要求を示す。

<table><thead><tr class="header"><th>CPUプロセッサ</th><th><p>600～1000MHz, 64bit シングルコア, 32KB L1 cache以上</p><p>ARMv8推奨</p><p>(ARMv8以外のCPU採用の場合はapis-mainの動作確認を行う必要あり)</p></th></tr></thead><tbody><tr class="odd"><td>DRAM</td><td>DDR3 1.6Gbps 1GB 以上</td></tr><tr class="even"><td>内部ストレージ</td><td>8GB以上</td></tr><tr class="odd"><td>Ethernet</td><td>20Mbps 1ポート以上, IPv4 IPv6 サポート</td></tr></tbody></table>

**OS要求**
----------

以下にapis-main用のコンピュータのOS要求を示す。

<table><thead><tr class="header"><th>種類</th><th><p>64bit OS, Linux 推奨</p><p>(Linux以外のOSの場合には採用前にapis-main動作確認を行う必要あり)</p></th></tr></thead><tbody><tr class="odd"><td>サイズ</td><td><p>IoT Boardの内部ストレージ容量次第</p><p>(apis-main等のLog保存場所用に3GB以上は確保すること)</p></td></tr><tr class="even"><td>動作ソフトウェア</td><td>AdoptOpenJDK (32/64bit)</td></tr><tr class="odd"><td>OSSライセンス</td><td>GPL等のコピーレフト型ライセンスの影響を避けるため、それらのライセンスを持つソフトウェアとapis-mainが1つの実行ファイルとなるOSは禁止 (例:RTOS)</td></tr><tr class="even"><td>その他</td><td>OS起動時にapis-main等の自動実行が行えること</td></tr><tr class="odd"><td></td><td>ssh login/scpファイル転送が行えること</td></tr><tr class="even"><td></td><td>logrotage等でログを定期的にリネーム/圧縮/削除が行えること</td></tr></tbody></table>

  [**1.** **用語・略語** 6]: #用語略語
  [**2.** **概要** 7]: #_Toc52292578
  [**ソフトウェア構成** 11]: #ソフトウェア構成
  [**3.1. ソフトウェアアーキテクチャ** 11]: #ソフトウェアアーキテクチャ
  [**3.2.** **apis-main内Service構成** 11]: #apis-main内service構成
  [**4.** **動作詳細説明** 13]: #_Toc52292583
  [**4.1.** **クラスタ構築** 13]: #クラスタ構築
  [**4.2.** **Grid Master選定** 13]: #grid-master選定
  [**4.3.** **apis-main処理** 14]: #apis-main処理
  [**4.4.** **Grid Master処理** 15]: #_Toc52292588
  [**4.5.** **電力融通処理** 16]: #電力融通処理
  [**4.6.** **各種ロック処理** 17]: #各種ロック処理
  [**5.** **通信仕様について** 19]: #通信仕様について
  [**5.1.** **apis-main – Device Driver間Web API** 19]: #apis-main-device-driver間web-api
  [**5.2.** **apis-main – Device Driver間の各get 系Web APIで期待される戻り値** 20]: #apis-main-device-driver間の各get-系web-apiで期待される戻り値
  [**5.2.1.** **“/dcdc/get” で期待される戻り値** 20]: #dcdcget-で期待される戻り値
  [**5.2.2.** **“/dcdc/get/status” で期待される戻り値** 21]: #dcdcgetstatus-で期待される戻り値
  [**5.2.3.** **“/dcdc/set?dig=&lt;Grid電流&gt;” で期待される戻り値** 22]: #dcdcsetdiggrid電流-で期待される戻り値
  [**5.2.4.** **“/dcdc/set?dvg=&lt;Grid電圧&gt;&drg=&lt;GridDroop率&gt;” で期待される戻り値** 22]: #dcdcsetdvggrid電圧drggriddroop率-で期待される戻り値
  [**5.2.5.** **“/dcdc/set?mode=&lt;Mode&gt;&dvg=&lt;Grid電圧&gt;&dig=&lt;Grid電流&gt;&dig=&lt;GridDroop率&gt;” で期待される戻り値** 23]: #dcdcsetmodemodedvggrid電圧diggrid電流diggriddroop率-で期待される戻り値
  [**5.2.6.** **“/battery/get” で期待される戻り値** 24]: #batteryget-で期待される戻り値
  [**5.2.7.** **“/all/get” で期待される戻り値** 24]: #allget-で期待される戻り値
  [**5.2.8.** **“/version/get” で期待される戻り値** 26]: #versionget-で期待される戻り値
  [**5.3.** **Device Driver – ハードウェア間通信仕様** 27]: #device-driver-ハードウェア間通信仕様
  [**6.** **設定ファイルについて** 28]: #設定ファイルについて
  [**6.1.** **config.json** 28]: #config.json
  [**6.2.** **hwConfig.json** 30]: #hwconfig.json
  [**6.3.** **scenario.json** 31]: #scenario.json
  [**6.4.** **policy.json** 35]: #policy.json
  [**6.5.** **cluster.xml** 39]: #cluster.xml
  [**6.6.** **logging.properties** 39]: #logging.properties
  [**6.7.** **start.sh** 40]: #start.sh
  [**6.8.** **stop-kill.sh** 40]: #stop-kill.sh
  [**6.9.** **key.pem** 41]: #key.pem
  [**6.10.** **cert.pem** 41]: #cert.pem
  [**7.** **DC Grid制御機能** 42]: #dc-grid制御機能
  [**7.1.** **電圧Referenceの選定** 42]: #電圧referenceの選定
  [**7.2.** **電圧Ramp Up** 42]: #電圧ramp-up
  [**7.3.** **電圧Reference権限獲得動作** 42]: #電圧reference権限獲得動作
  [**7.4.** **電流補正機能** 43]: #電流補正機能
  [**7.5.** **Constant Voltage(CV)移動** 44]: #constant-voltagecv移動
  [**7.6.** **電圧最適化** 45]: #電圧最適化
  [**8.** **Grid Master Data収集** 47]: #grid-master-data収集
  [**9.** **Log取得・保存機能** 50]: #log取得保存機能
  [**9.1.** **apis-main動作Log** 50]: #apis-main動作log
  [**9.2.** **apis-main動作Log出力先** 51]: #apis-main動作log出力先
  [**9.3.** **電力融通Log** 52]: #電力融通log
  [**10.** **Error処理・保護機能** 54]: #error処理保護機能
  [**10.1.** **apis-main Error処理** 54]: #apis-main-error処理
  [**11.** **その他の機能** 58]: #その他の機能
  [**11.1.** **トポロジー検出** 58]: #トポロジー検出
  [**11.2.** **Gateway機能** 64]: #gateway機能
  [**12.** **安全確認機能** 66]: #安全確認機能
  [**12.1.** **Global Safety Check** 66]: #global-safety-check
  [**12.2.** **Local Safety Check** 66]: #local-safety-check
  [**12.2.1.静的Local Safety Check** 66]: #静的local-safety-check
  [**12.2.2. 動的Local Safety Check** 68]: #動的local-safety-check
  [**13.** **セキュリティ** 69]: #セキュリティ
  [**13.1.** **apis-main間通信セキュリティ** 69]: #apis-main間通信セキュリティ
  [**13.2.** **Device Driver アクセス制限** 69]: #device-driver-アクセス制限
  [**14.** **プライバシー** 69]: #プライバシー
  [**15.** **OSSライセンス** 70]: #ossライセンス
  [**16.** **動作環境** 71]: #動作環境
  [**16.1.** **ハードウェア要求** 71]: #ハードウェア要求
  [**16.2.** **OS要求** 71]: #os要求
