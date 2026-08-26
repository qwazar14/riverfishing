# The 3D rod on 26.x — what the API actually is

Everything here was read out of `minecraft-merged-13d1814ffa-26.2.jar` with `javap`, not remembered.
Where an earlier note in this repo said otherwise, this file is right and that note was wrong.

## First, a correction

I previously wrote that five of the six types the 1.21.1 rod renderer stands on were deleted on 26.2.
Two of them had **moved**, not gone:

| Type | 26.2 |
|---|---|
| `RenderType` | **moved** → `net.minecraft.client.renderer.rendertype.RenderType` |
| `BakedQuad` | **moved** → `net.minecraft.client.resources.model.geometry.BakedQuad` |
| `ModelManager` | still `net.minecraft.client.resources.model.ModelManager` |
| `BakedModel` | **gone** |
| `ItemRenderer` | **gone** |
| `MultiBufferSource` | **gone** |
| `BlockEntityWithoutLevelRenderer` | **gone** |

So the architecture change is real, and smaller than I said. Four types to replace, not five, and two of
the "losses" were a package rename I checked for at the old path.

## The shape of the new pipeline

1.21.1 hands a renderer a `MultiBufferSource` and it **writes vertices**. 26.x hands it a
`SubmitNodeCollector` and it **submits nodes** — the engine batches and draws them later. That single
change is what makes the old renderer untranslatable line by line, and it is also why the port is
tractable: the thing we want to submit already exists as a node type.

### The BEWLR replacement

```java
public interface SpecialModelRenderer<T> {
    void submit(T arg, PoseStack pose, SubmitNodeCollector collector,
                int light, int overlay, boolean foil, int outline);
    void getExtents(Consumer<Vector3fc> consumer);
    T extractArgument(ItemStack stack);
}
```

`NoDataSpecialModelRenderer` is the `T = Void` convenience for renderers that need nothing off the stack.
**We need the stack** (rod class, fitted reel, bend state), so we implement the generic form and pull what
we need in `extractArgument` — which is an improvement on BEWLR, where rendering read the stack directly
and could see it change under it.

It is bound to an item by an `assets/riverfishing/items/<id>.json` model definition, the same file whose
absence made `groundbait_soil` a pink square. Vanilla's `ChestSpecialRenderer` and `ShieldSpecialRenderer`
are the worked examples to copy the registration shape from.

### Drawing a segment

`BakedModel` is gone, so a segment's geometry is reached through the model manager and a render state:

```java
ItemModel model = mc.getModelManager().getItemModel(RodModelLayers.segment(rodKey, i));
model.update(state, stack, resolver, ctx, level, owner, seed);   // fills the state
state.submit(pose, collector, light, overlay, outline);           // draws it under OUR pose
```

That is the exact analogue of the old `ir.render(bakedModel, …, pose, buffers, …)`: we own the
`PoseStack`, so the bone chain is built the same way it is on 1.21.1 — push, rotate at the joint,
draw the segment, recurse. **The bend maths ports unchanged.** Only the draw call differs.

`ItemStackRenderState.LayerRenderState` also offers `prepareQuadList()` and `setLocalTransform(Matrix4fc)`
if we ever want to bypass the model layer and hand over quads directly. Not needed for the chain.

### Drawing the line

```java
collector.submitCustomGeometry(pose, RenderType, CustomGeometryRenderer);
```

Arbitrary geometry with our own pose and render type — the direct replacement for writing line vertices
into a buffer. Note this solves the 1.20.1 problem for free: geometry is submitted **with a pose**, so
there is no identity-matrix-means-view-space assumption to get wrong.

## What this means for the work

- The bone chain, the joint tables, the bend curve, the per-rod springs and the tip capture are all
  **arithmetic** and port as they are.
- What is genuinely new is the registration seam and the draw calls — bounded, and with vanilla
  examples to follow.
- `RodModelLayers`, `RodPhysics`, `RodClientSettings` and `RodHandTransform` are version-neutral or
  nearly so and can come across close to unchanged.

## What is NOT yet decided

- Whether the hand pose on 26.x carries the camera the way 1.21.1's does. The 1.20.1 port cost three
  rounds on exactly that question and was settled by measuring in game with `/rfrod tipinfo`, not by
  reading. **Port that command first and read it before writing the line anchor**, not after.
- Whether both Stonecutter targets agree here. 26.1.2 and 26.2 must be checked separately; they have
  already differed once this release (`setScreen` → `setScreenAndShow`).
