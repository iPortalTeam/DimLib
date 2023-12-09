# DimLib

This is a Fabric mod that can:
* Add and remove dimensions when the server is running or when the server is initializing.
* Synchronize dimension info to client when dimension changes when the client has the mod.
* Allow suppressing the "Worlds using Experimental Settings are not supported" warning, through config or code.

This mod is required on server but not required on client. If the client does not have the mod, the command completion of dimension id will not update if dimension changes.

All APIs are in `DimensionAPI` class. You can refer to the javadoc.