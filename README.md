# DimLib

This is a Fabric mod that can:
* Add and remove dimensions when the server is running or when the server is initializing.
* Synchronize dimension info to client when dimension changes if the client has the mod.
* Allow suppressing the "Worlds using Experimental Settings are not supported" warning, through config or code.

For the dynamic dimension functionality, this mod is required on server but not required on client. If the client does not have the mod, the command completion of dimension id will not update if dimension changes.

If you only want the functionality of disabling the warning, this mod is only required on client.

All APIs are in `DimensionAPI` class. You can refer to the javadoc.

### Commands

#### `/dims add_dimension <newDimensionId> <preset>`

Dynamically add a new dimension based on a preset.

Example: `/dims add_dimension "aaa:ccc" skyland`

#### `/dims clone_dimension <templateDimension> <newDimensionID>`

Dynamically add a new dimension. That new dimension's dimension type and chunk generator will be the same as the `templateDimension`.

This command only clones the dimension type and world generator. It will not clone the things in world (blocks, entities, ...).

Example: `/dims clone_dimension minecraft:overworld "aaa:bbb"` will dynamically add dimension `aaa:bbb` whiches world generation is the same as the overworld.

#### `/dims remove_dimension <dimension>`

Dynamically remove a dimension.

This command will not delete the world saving of that dimension.

#### `/dims view_dim_config <dimension>`

Show the dimension config of a dimension. It includes the dimension type id and chunk generator config.