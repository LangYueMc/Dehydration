package net.dehydration.item;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import eu.midnightdust.puddles.Puddles;
import net.dehydration.access.ThirstManagerAccess;
import net.dehydration.init.ConfigInit;
import net.dehydration.init.EffectInit;
import net.dehydration.init.SoundInit;
import net.dehydration.misc.ThirstTooltipData;
import net.dehydration.thirst.ThirstManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Blocks;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.item.TooltipData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.stat.Stats;
import net.minecraft.tag.BiomeTags;
import net.minecraft.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

// Thanks to Pois1x for the texture

public class Leather_Flask extends Item {
    public int addition;

    public Leather_Flask(int waterAddition, Settings settings) {
        super(settings);
        this.addition = waterAddition + ConfigInit.CONFIG.extra_flask_size;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack itemStack = user.getStackInHand(hand);
        NbtCompound tags = itemStack.getNbt();
        HitResult hitResult = raycast(world, user, RaycastContext.FluidHandling.SOURCE_ONLY);
        BlockPos blockPos = ((BlockHitResult) hitResult).getBlockPos();

        if (hitResult.getType() == HitResult.Type.BLOCK && world.canPlayerModifyAt(user, blockPos) && world.getFluidState(blockPos).isIn(FluidTags.WATER) && itemStack.hasNbt()) {
            if (user.isSneaking() && tags.getInt("leather_flask") != 0) {
                tags.putInt("leather_flask", 0);
                world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundInit.EMPTY_FLASK_EVENT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                return TypedActionResult.consume(itemStack);
            }
            if (tags.getInt("leather_flask") < 2 + addition) {
                int fillLevel = 2 + addition;
                int waterPurity = 2;

                boolean isEmpty = tags.getInt("leather_flask") == 0;
                boolean isDirtyWater = tags.getInt("purified_water") == 2;
                if (!isEmpty && !isDirtyWater) {
                    waterPurity = 1;
                }

                if (FabricLoader.getInstance().isModLoaded("puddles") && world.getBlockState(blockPos) == Puddles.Puddle.getDefaultState()) {
                    if (!world.isClient) {
                        world.setBlockState(blockPos, Blocks.AIR.getDefaultState());
                    }
                    if (!isEmpty && !isDirtyWater) {
                        fillLevel = 2;
                        waterPurity = 0;
                    }
                }

                boolean riverWater = world.getBiome(blockPos).isIn(BiomeTags.IS_RIVER);
                if (riverWater && (isEmpty || (!isEmpty && !isDirtyWater))) {
                    waterPurity = 0;
                }
                world.playSound(user, user.getX(), user.getY(), user.getZ(), SoundInit.FILL_FLASK_EVENT, SoundCategory.NEUTRAL, 1.0F, 1.0F);
                tags.putInt("purified_water", waterPurity);
                tags.putInt("leather_flask", fillLevel);
                return TypedActionResult.consume(itemStack);
            }
        }
        if (itemStack.hasNbt() && tags.getInt("leather_flask") == 0) {
            return TypedActionResult.pass(itemStack);
        } else {
            return ItemUsage.consumeHeldItem(world, user, hand);
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        PlayerEntity playerEntity = user instanceof PlayerEntity ? (PlayerEntity) user : null;
        NbtCompound tags = stack.getNbt();
        if (!stack.hasNbt() || tags != null && tags.getInt("leather_flask") > 0) {
            if (playerEntity instanceof ServerPlayerEntity) {
                Criteria.CONSUME_ITEM.trigger((ServerPlayerEntity) playerEntity, stack);
            }
            if (playerEntity != null) {
                playerEntity.incrementStat(Stats.USED.getOrCreateStat(this));
                if (!playerEntity.isCreative()) {
                    if (!stack.hasNbt()) {
                        tags = new NbtCompound();

                        tags.putInt("leather_flask", 2 + addition);
                        tags.putInt("purified_water", 0);
                        stack.setNbt(tags);
                    }
                    tags.putInt("leather_flask", tags.getInt("leather_flask") - 1);
                    ThirstManager thirstManager = ((ThirstManagerAccess) playerEntity).getThirstManager(playerEntity);
                    thirstManager.add(ConfigInit.CONFIG.flask_thirst_quench);

                    if (tags.getInt("purified_water") == 2 && world.random.nextFloat() <= ConfigInit.CONFIG.flask_dirty_thirst_chance) {
                        if (!world.isClient) {
                            playerEntity.addStatusEffect(new StatusEffectInstance(EffectInit.THIRST, ConfigInit.CONFIG.flask_dirty_thirst_duration, 1, false, false, true));
                        }
                    } else if (tags.getInt("purified_water") == 1 && world.random.nextFloat() <= ConfigInit.CONFIG.flask_dirty_thirst_chance * 0.5F) {
                        if (!world.isClient) {
                            playerEntity.addStatusEffect(new StatusEffectInstance(EffectInit.THIRST, ConfigInit.CONFIG.flask_dirty_thirst_duration, 0, false, false, true));
                        }
                    }
                }
            }
        }
        return stack;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 32;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        NbtCompound tags = stack.getNbt();
        if (!stack.hasNbt() || (tags != null && tags.getInt("leather_flask") > 0)) {
            return UseAction.DRINK;
        } else
            return UseAction.NONE;
    }

    @Override
    public void onCraft(ItemStack stack, World world, PlayerEntity player) {
        if (!stack.hasNbt()) {
            NbtCompound tags = new NbtCompound();
            tags.putInt("leather_flask", 0);
            stack.setNbt(tags);
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound tags = stack.getNbt();
        if (tags != null) {
            tooltip.add(Text.translatable("item.dehydration.leather_flask.tooltip", tags.getInt("leather_flask"), addition + 2).formatted(Formatting.GRAY));
            if (tags.getInt("leather_flask") != 2) {
                String string = "Dirty Water";
                Formatting formatting = Formatting.DARK_GREEN;
                if (tags.getInt("purified_water") == 1) {
                    string = "Impurified Water";
                    formatting = Formatting.DARK_AQUA;
                } else if (tags.getInt("purified_water") == 0) {
                    string = "Purified Water";
                    formatting = Formatting.AQUA;
                }
                tooltip.add(Text.translatable("item.dehydration.leather_flask.tooltip3", string).formatted(formatting));
            }
        } else
            tooltip.add(Text.translatable("item.dehydration.leather_flask.tooltip2", addition + 2).formatted(Formatting.GRAY));
        super.appendTooltip(stack, world, tooltip, context);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        if (stack.hasNbt() && stack.getNbt().contains("leather_flask")) {
            if (stack.getNbt().getInt("leather_flask") == 0)
                return Optional.empty();

            return Optional.of(new ThirstTooltipData(stack.getNbt().getInt("purified_water"), stack.getNbt().getInt("leather_flask") * ConfigInit.CONFIG.flask_thirst_quench));
        }
        return Optional.of(new ThirstTooltipData(0, (2 + this.addition) * ConfigInit.CONFIG.flask_thirst_quench));
    }

    public static boolean isFlaskEmpty(ItemStack stack) {
        NbtCompound tags = stack.getNbt();
        if (tags != null) {
            if (tags.getInt("leather_flask") != 0)
                return false;
            else
                return true;
        } else
            return false;
    }

    public static boolean isFlaskFull(ItemStack stack) {
        NbtCompound tags = stack.getNbt();
        if (tags != null) {
            if (tags.getInt("leather_flask") >= ((Leather_Flask) stack.getItem()).addition + 2)
                return true;
            else
                return false;
        } else
            return true;
    }

    // purified_water: 0 = purified, 1 impurified, 2 dirty

}
