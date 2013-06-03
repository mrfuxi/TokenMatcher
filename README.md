TokenMatcher
============

Example of usage:

<response>
	<lst name="responseHeader">
		<int name="status">0</int>
		<int name="QTime">2</int>
		<lst name="params">
			<str name="echoParams">explicit</str>
			<str name="fl">
				id,name,manu,mtokens(name,"hard sata drives 250 500 SpinPoint samsung")
			</str>
			<str name="q">hard</str>
		</lst>
	</lst>
	<result name="response" numFound="2" start="0">
		<doc>
			<str name="id">SP2514N</str>
			<str name="name">
				Samsung SpinPoint P120 SP2514N - hard drive - 250 GB - ATA-133
			</str>
			<str name="manu">Samsung Electronics Co. Ltd.</str>
			<str name="mtokens(name,"hard sata drives 250 500 SpinPoint samsung")">drives 250 | SpinPoint samsung</str>
		</doc>
		<doc>
			<str name="id">6H500F0</str>
			<str name="name">
				Maxtor DiamondMax 11 - hard drive - 500 GB - SATA-300
			</str>
			<str name="manu">Maxtor Corp.</str>
			<str name="mtokens(name,"hard sata drives 250 500 SpinPoint samsung")">hard sata drives</str>
		</doc>
	</result>
</response>
