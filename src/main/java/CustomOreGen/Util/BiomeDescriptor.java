package CustomOreGen.Util;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;
import CustomOreGen.Server.DistributionSettingMap.Copyable;

import org.eclipse.collections.impl.map.mutable.primitive.*;
import org.eclipse.collections.api.map.primitive.*;
import org.eclipse.collections.api.iterator.*;

public class BiomeDescriptor implements Copyable<BiomeDescriptor>
{
    protected ArrayList<Descriptor> _descriptors = new ArrayList<Descriptor>();
    protected MutableIntFloatMap _matches = new IntFloatHashMap().asSynchronized();
    protected boolean _compiled = false;
    
    private String name;

    public BiomeDescriptor()
    {
        this.clear();
    }

    public BiomeDescriptor(String descriptor)
    {
        this.set(descriptor);
    }
    
    public void copyFrom(BiomeDescriptor source)
    {
        this._descriptors = new ArrayList<Descriptor>(source._descriptors);
        this._matches = new IntFloatHashMap(source._matches).asSynchronized();
        this._compiled = source._compiled;
    }
    
    public String getName() {
    	return name;
    }
    
    public void setName(String name) {
    	this.name = name;
    }
    
    public BiomeDescriptor set(String descriptor)
    {
        this.clear();

        if (descriptor != null)
        {
            this.add(descriptor);
        }

        return this;
    }

    public BiomeDescriptor add(String descriptor)
    {
        return this.add(descriptor, 1.0F);
    }

    public BiomeDescriptor add(String descriptor, float weight)
    {
        return this.add(descriptor, 1.0F, new BiomeRestriction(), false);
    }
    
    public BiomeDescriptor add(String descriptor, float weight, BiomeRestriction climate, boolean describesType)
    {
        if (descriptor != null && weight != 0.0F)
        {
            this._compiled = false;
            this._descriptors.add(new Descriptor(descriptor, weight, climate, describesType));
        }

        return this;
    }
    
    public BiomeDescriptor addAll(BiomeDescriptor descriptor, float weight) {
    	this._compiled = false;
    	if (weight == 1.0F) {
    		this._descriptors.addAll(descriptor._descriptors);
    	} else {
    		for (Descriptor desc : descriptor._descriptors) {
    			add(desc.description, desc.weight * weight, desc.climate, desc.describesType);
    		}
    	}
    	return this;
	}

    public BiomeDescriptor clear()
    {
        this._compiled = false;
        this._descriptors.clear();
        return this;
    }

    public List<Descriptor> getDescriptors()
    {
        return Collections.unmodifiableList(this._descriptors);
    }

    protected void add(BiomeGenBase biome, float weight)
    {
        if (biome != null && weight != 0.0F)
        {
            float currentValue = this._matches.get(biome.biomeID);

            weight += currentValue;

            this._matches.put(biome.biomeID, weight);
        }
    }

    protected float matchingWeight(BiomeGenBase biome)
    {
        float totalWeight = 0.0F;
        
        String name = biome.biomeName;
        
        for (Descriptor desc : this._descriptors) {
            Matcher matcher;
            if (!desc.climate.isCompatible(biome))
            	continue;
            
            if (desc.describesType) {
            	BiomeDictionary.Type type = BiomeDictionary.Type.valueOf(desc.description.toUpperCase());
            	// instead of this, because we do not want to add a new type if it does not exist:
            	//BiomeDictionary.Type type = BiomeDictionary.Type.getType(desc.description);
            	if (BiomeDictionary.isBiomeOfType(biome, type))
            	{
            		totalWeight += desc.weight;
            	}
            } else {
            	if (name != null)
            	{
            		matcher = desc.getPattern().matcher(name);

            		if (matcher.matches())
            		{
            			totalWeight += desc.weight;
            		}
            	}
            }
        }
        return totalWeight;
    }

    protected void compileMatches()
    {
        if (!this._compiled)
        {
            this._compiled = true;
            this._matches.clear();
            
            for (BiomeGenBase biome : BiomeGenBase.getBiomeGenArray()) {
                if (biome != null)
                {
                	this.add(biome, this.matchingWeight(biome));
                }
            }
        }
    }
    
    public float getWeight(BiomeGenBase biome)
    {
        this.compileMatches();
        float value = this._matches.get(biome.biomeID);
        return value;
    }

    public boolean matchesBiome(BiomeGenBase biome, Random rand)
    {
        float weight = this.getWeight(biome);

        if (weight <= 0.0F)
        {
            return false;
        }
        else if (weight < 1.0F)
        {
            if (rand == null)
            {
                rand = new org.bogdang.modifications.random.XSTR();
            }

            return rand.nextFloat() < weight;
        }
        else
        {
            return true;
        }
    }

    public BiomeGenBase getMatchingBiome(Random rand)
    {
        this.compileMatches();
        float value = -1.0F;
        
        for (MutableIntIterator iterator = this._matches.keySet().intIterator();iterator.hasNext(); ) {
        	int entry = iterator.next();
        	float weight = this._matches.get(entry);
            BiomeGenBase biome = BiomeGenBase.getBiome(entry);

            if (weight > 0.0F)
            {
                if (weight >= 1.0F)
                {
                    return biome;
                }

                if (value < 0.0F)
                {
                    if (rand == null)
                    {
                        rand = new org.bogdang.modifications.random.XSTR();
                    }

                    value = rand.nextFloat();
                }

                value -= weight;

                if (value < 0.0F)
                {
                    return biome;
                }
            }

        }
        return null;
    }

    public float getTotalMatchWeight()
    {
        this.compileMatches();
        float weight = 0.0F;
        
        for (MutableFloatIterator iterator = this._matches.values().floatIterator();iterator.hasNext(); ) {
        	float val = iterator.next();
        	if (val > 0.0F)
            {
                weight += val;
            }
        }
        return weight;
    }

    public String toString()
    {
        if (this._descriptors.size()==0) {
            return "[no biomes]";
        } else if (this._descriptors.size()==1) {
            return ((Descriptor)this._descriptors.get(0)).toString();
        } else {
            return this._descriptors.toString();
        }
    }

    public String[] toDetailedString()
    {
        this.compileMatches();
        String[] breakdown = new String[this._matches.size() + 1];
        breakdown[0] = this._matches.size() + " biome matches";

        if (this._matches.size() > 0)
        {
            breakdown[0] = breakdown[0] + ':';
        }

        int i = 1;

        for (MutableIntIterator iterator = this._matches.keySet().intIterator();iterator.hasNext(); ) {
        	int entry = iterator.next();
        	float weight = this._matches.get(entry);
            BiomeGenBase biome = BiomeGenBase.getBiome(entry);

            if (biome == null)
            {
                breakdown[i] = "[??]";
            }
            else
            {
                breakdown[i] = biome.biomeName;
            }

            breakdown[i] = breakdown[i] + " - " + weight;
            ++i;
        }
        
        return breakdown;
    }

    private static class Descriptor
    {
        public final String description;
        public final float weight;
        public final BiomeRestriction climate;
        public final boolean describesType;
        private Pattern pattern = null;

        public Descriptor(String description, float weight, BiomeRestriction climate, boolean describesType)
        {
            this.description = description;
            this.weight = weight;
			this.climate = climate;
            this.describesType = describesType;
        }

        public Pattern getPattern()
        {
            if (this.pattern == null)
            {
                this.pattern = Pattern.compile(this.description, Pattern.CASE_INSENSITIVE);
            }

            return this.pattern;
        }

        public String toString()
        {
            return this.description + " - " + Float.toString(this.weight);
        }
    }
    
    public static class BiomeRestriction {
    	public final float minTemperature, maxTemperature;
        public final float minRainfall, maxRainfall;
        public final int minTreesPerChunk, maxTreesPerChunk;
        public final float minHeightVariation, maxHeightVariation;
        
        public BiomeRestriction(float minTemperature, float maxTemperature, float minRainfall, float maxRainfall,
        		int minTreesPerChunk, int maxTreesPerChunk, float minHeightVariation, float maxHeightVariation) {
        	this.minTemperature = minTemperature;
			this.maxTemperature = maxTemperature;
			this.minRainfall = minRainfall;
			this.maxRainfall = maxRainfall;
			this.minTreesPerChunk = minTreesPerChunk;
			this.maxTreesPerChunk = maxTreesPerChunk;
			this.minHeightVariation = minHeightVariation;
			this.maxHeightVariation = maxHeightVariation;
        }
        
        public BiomeRestriction() {
        	this.minTemperature = this.minRainfall = this.minHeightVariation = Float.NEGATIVE_INFINITY;
			this.maxTemperature = this.maxRainfall = this.maxHeightVariation = Float.POSITIVE_INFINITY;
			this.minTreesPerChunk = Integer.MIN_VALUE;
			this.maxTreesPerChunk = Integer.MAX_VALUE;
        }
        
        public boolean isCompatible(BiomeGenBase biome) {
			return biome.temperature >= minTemperature && biome.temperature <= maxTemperature &&
				   biome.rainfall >= minRainfall && biome.rainfall <= maxRainfall &&
				   biome.theBiomeDecorator.treesPerChunk >= minTreesPerChunk &&
				   biome.theBiomeDecorator.treesPerChunk <= maxTreesPerChunk &&
				   biome.heightVariation >= minHeightVariation &&
				   biome.heightVariation <= maxHeightVariation;
		}
    }

}
