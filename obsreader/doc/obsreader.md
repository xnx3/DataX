# DataX OBSReader 说明


------------
针对华为云OBS对象存储的插件，由潍坊雷鸣云网络科技 www.leimingyun.com 开发。

## 1 快速介绍

OBSReader提供了读取OBS数据存储的能力。在底层实现上，OBSReader使用OBS官方Java SDK获取OBS数据，并转换为DataX传输协议传递给Writer。

* OBS 产品介绍, 参看[[华为云OBS](https://support.huaweicloud.com/obs/index.html)]
* OBS Java SDK, 参看[[华为云OBS Java SDK](https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0001.html)]

## 2 功能与限制

OBSReader实现了从OBS读取数据并转为DataX协议的功能，OBS本身是无结构化数据存储，对于DataX而言，OBSReader实现上类比TxtFileReader，有诸多相似之处。目前OBSReader支持功能如下：

1. 支持且仅支持读取TXT的文件，且要求TXT中shema为一张二维表。

2. 支持类CSV格式文件，自定义分隔符。

3. 支持多种类型数据读取(使用String表示)，支持列裁剪，支持列常量

4. 支持递归读取、支持文件名过滤。

5. 支持文本压缩，现有压缩格式为zip、gzip、bzip2。注意，一个压缩包不允许多文件打包压缩。

6. 多个object可以支持并发读取。

我们暂时不能做到：

1. 单个Object(File)支持多线程并发读取，这里涉及到单个Object内部切分算法。二期考虑支持。

2.  单个Object在压缩情况下，从技术上无法支持多线程并发读取。


## 3 功能说明


### 3.1 配置样例

```json
{
    "job": {
        "setting": {},
        "content": [
            {
                "reader": {
                    "name": "obsreader",
                    "parameter": {
                        "endpoint": "https://obs.cn-north-4.myhuaweicloud.com",
                        "accessId": "yourAccessKey",
                        "accessKey": "yourSecretKey",
                        "bucket": "yourBucket",
                        "object": [
                            "obstest/*"
                        ],
                        "column": [
                            {
                                "type": "long",
                                "index": 0
                            },
                            {
                                "type": "string",
                                "value": "name"
                            },
                            {
                                "type": "date",
                                "index": 1,
                                "format": "yyyy-MM-dd"
                            }
                        ],
                        "encoding": "UTF-8",
                        "fieldDelimiter": "\t",
                        "compress": "gzip"
                    }
                },
                "writer": {}
            }
        ]
    }
}
```

### 3.2 参数说明

* **endpoint**

	* 描述：OBS Server的EndPoint地址，例如https://obs.cn-north-4.myhuaweicloud.com。

	* 必选：是 <br />

	* 默认值：无 <br />

* **accessId**

	* 描述：OBS的accessId <br />

	* 必选：是 <br />

	* 默认值：无 <br />

* **secretKey**

	* 描述：OBS的secretKey  <br />

	* 必选：是 <br />

	* 默认值：无 <br />

* **bucket**

	* 描述：OBS的bucket  <br />

	* 必选：是 <br />

	* 默认值：无 <br />

* **object**

	* 描述：OBS的object信息，注意这里可以支持填写多个Object。 <br />

		当指定单个OBS Object，OBSReader暂时只能使用单线程进行数据抽取。二期考虑在非压缩文件情况下针对单个Object可以进行多线程并发读取。

		当指定多个OBS Object，OBSReader支持使用多线程进行数据抽取。线程并发数通过通道数指定。

		当指定通配符，OBSReader尝试遍历出多个Object信息。例如: 指定/*代表读取bucket下游所有的Object，指定/obstest/\*代表读取obstest目录下游所有的Object。

		**特别需要注意的是，DataX会将一个作业下同步的所有Object视作同一张数据表。用户必须自己保证所有的Object能够适配同一套schema信息。**

	* 必选：是 <br />

	* 默认值：无 <br />

* **column**

	* 描述：读取字段列表，type指定源数据的类型，index指定当前列来自于文本第几列(以0开始)，value指定当前类型为常量，不从源头文件读取数据，而是根据value值自动生成对应的列。 <br />

		默认情况下，用户可以全部按照String类型读取数据，配置如下：

		```json
			"column": ["*"]
		```

		用户可以指定Column字段信息，配置如下：

		```json
		{
           "type": "long",
           "index": 0    //从OBS文本第一列获取int字段
        },
        {
           "type": "string",
           "value": "name"  //从OBSReader内部生成name的字符串字段作为当前字段
        }
		```

		对于用户指定Column信息，type必须填写，index/value必须选择其一。

	* 必选：是 <br />

	* 默认值：全部按照string类型读取 <br />

* **fieldDelimiter**

	* 描述：读取的字段分隔符 <br />

	* 必选：是 <br />

	* 默认值：, <br />

* **compress**

	* 描述：文本压缩类型，默认不填写意味着没有压缩。支持压缩类型为zip、gzip、bzip2。 <br />

	* 必选：否 <br />

	* 默认值：不压缩 <br />

* **encoding**

	* 描述：读取文件的编码配置，目前只支持utf-8/gbk。<br />

 	* 必选：否 <br />

 	* 默认值：utf-8 <br />

* **nullFormat**

	* 描述：文本文件中无法使用标准字符串定义null(空指针)，DataX提供nullFormat定义哪些字符串可以表示为null。<br />

		 例如如果用户配置: nullFormat="\N"，那么如果源头数据是"\N"，DataX视作null字段。

 	* 必选：否 <br />

 	* 默认值：\N <br />

* **skipHeader**

	* 描述：类CSV格式文件可能存在表头为标题情况，需要跳过。默认不跳过。<br />

 	* 必选：否 <br />

 	* 默认值：false <br />


* **csvReaderConfig**

	* 描述：读取CSV类型文件参数配置，Map类型。读取CSV类型文件使用的CsvReader进行读取，会有很多配置，不配置则使用默认值。<br />

 	* 必选：否 <br />
 
 	* 默认值：无 <br />

        
常见配置：

```json
"csvReaderConfig":{
        "safetySwitch": false,
        "skipEmptyRecords": false,
        "useTextQualifier": false
}
```

所有配置项及默认值,配置时 csvReaderConfig 的map中请**严格按照以下字段名字进行配置**：

```
boolean caseSensitive = true;
char textQualifier = 34;
boolean trimWhitespace = true;
boolean useTextQualifier = true;//是否使用csv转义字符
char delimiter = 44;//分隔符
char recordDelimiter = 0;
char comment = 35;
boolean useComments = false;
int escapeMode = 1;
boolean safetySwitch = true;//单列长度是否限制100000字符
boolean skipEmptyRecords = true;//是否跳过空行
boolean captureRawRecord = true;
```


### 3.3 类型转换


OBS本身不提供数据类型，该类型是DataX OBSReader定义：

| DataX 内部类型| OBS 数据类型    |
| -------- | -----  |
| Long     |Long |
| Double   |Double|
| String   |String|
| Boolean  |Boolean |
| Date     |Date |

其中：

* OBS Long是指OBS文本中使用整形的字符串表示形式，例如"19901219"。
* OBS Double是指OBS文本中使用Double的字符串表示形式，例如"3.1415"。
* OBS Boolean是指OBS文本中使用Boolean的字符串表示形式，例如"true"、"false"。不区分大小写。
* OBS Date是指OBS文本中使用Date的字符串表示形式，例如"2014-12-31"，Date可以指定format格式。

## 4 性能报告

略

## 5 约束限制

略

## 6 FAQ

略

