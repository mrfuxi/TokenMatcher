TokenMatcher
============

Funtion:

How to use:
- Add jar to be loaded
- Add valueSourceParser like: <valueSourceParser name="mtokens" class="com.fuxi.MatchingTokensParser" />

Helper component:

How to use:
- Add jar to be loaded
- Define component <searchComponent name="mth" class="com.fuxi.MatchingTokensHelper" />
- Add component to a handler <arr name="last-components"><str>mth</str></arr>


Example of usage:

```
<response>
	<lst name="responseHeader">
		<int name="status">0</int>
		<int name="QTime">3</int>
		<lst name="params">
			<str name="df">name</str>
			<str name="echoParams">explicit</str>
			<str name="fl">id,name,manu,cat,mtokens(name,"hard sata drives 250 500 SpinPoint samsung")</str>
			<str name="q">hard</str>
			<str name="terms">hard sata drives 250 500 SpinPoint samsung</str>
		</lst>
	</lst>
	<result name="response" numFound="2" start="0">
		<doc>
			<str name="id">SP2514N</str>
			<str name="name">Samsung SpinPoint P120 SP2514N - hard drive - 250 GB - ATA-133</str>
			<str name="manu">Samsung Electronics Co. Ltd.</str>
			<arr name="cat">
				<str>electronics</str>
				<str>hard drive</str>
			</arr>
			<str name="mtokens(name,"hard sata drives 250 500 SpinPoint samsung")">drives 250 | SpinPoint samsung</str>
		</doc>
		<doc>
			<str name="id">6H500F0</str>
			<str name="name">Maxtor DiamondMax 11 - hard drive - 500 GB - SATA-300</str>
			<str name="manu">Maxtor Corp.</str>
			<arr name="cat">
				<str>electronics</str>
				<str>hard drive</str>
			</arr>
			<str name="mtokens(name,"hard sata drives 250 500 SpinPoint samsung")">hard sata drives</str>
		</doc>
	</result>
	<lst name="mth">
		<lst name="name">
			<str name="0">hard</str>
			<str name="1">sata</str>
			<str name="2">drives</str>
			<str name="3">250</str>
			<str name="4">500</str>
			<str name="5">SpinPoint</str>
			<str name="6">samsung</str>
		</lst>
	</lst>
</response>
```
