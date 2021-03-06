package xiong.hdstats.opt.randgraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import Jama.Matrix;
import xiong.hdstats.opt.AveragedChainedRiskFunction;
import xiong.hdstats.opt.AveragedRiskFunction;
import xiong.hdstats.opt.ChainedFunction;
import xiong.hdstats.opt.MultiVariable;
import xiong.hdstats.opt.RiskFunction;

public class RandomGraphChainedRiskFunction extends AveragedRiskFunction implements ChainedFunction {
	public RandomGraph rn;
	private int index = 0;
	public Set<Integer> miniBatch = new HashSet<Integer>();
	
	public RandomGraphChainedRiskFunction(AveragedChainedRiskFunction crf, RandomGraph rg){
		for(RiskFunction f:crf.funcs)
			addFunction((ChainedFunction)f);
		this.rn = rg;
		rn.randStart();

	}

	public RandomGraphChainedRiskFunction(List<ChainedFunction> fs, RandomGraph rg) {
		// super(fs);
		for(ChainedFunction f:fs)
			addFunction(f);
		this.rn = rg;
		rn.randStart();
	}

	public void addFunction(RiskFunction func) {
		this.funcs.add(func);
	}

	@Override
	public Matrix func(MultiVariable input) {
		// TODO Auto-generated method stub
		if (funcs.size() < 1)
			return new Matrix(0, 0);
		Matrix m = this.funcs.get(0).func(input).copy();
		for (int i = 1; i < funcs.size(); i++) {
			m.plus(this.funcs.get(i).func(input).copy());
		}
		return m.times(1.0 / funcs.size());
	}

	@Override
	public MultiVariable gradient(MultiVariable input) {
		// TODO Auto-generated method stub
		if (funcs.size() < 1)
			return null;
		MultiVariable m = this.funcs.get(0).gradient(input).clone();
		for (int i = 1; i < funcs.size(); i++) {
			m = m.plus(this.funcs.get(i).gradient(input).clone());
		}
		return m.times(1.0 / funcs.size());
	}

	public void toNextRandom() {
		index = Math.abs(rn.nextInt()) % this.funcs.size();
	}

	public void toNextRandomMiniBatch(int miniSize) {
		int n = funcs.size();
		while (miniBatch.size() < miniSize) {
			int i = Math.abs(rn.nextInt()) % n;
			miniBatch.add(i);
		}
	}

	public MultiVariable randomGradient(MultiVariable input) {
		return this.funcs.get(index).gradient(input);
	}

	public MultiVariable randomSetGradient(MultiVariable input, int miniSize) {
		if (miniBatch.size() != miniSize)
			return null;
		List<Integer> miniBatchList = new ArrayList<Integer>(miniBatch);
		MultiVariable m = this.funcs.get(miniBatchList.get(0)).gradient(input).clone();
		for (int i = 1; i < miniBatchList.size(); i++) {
			m = m.plus(this.funcs.get(miniBatchList.get(i)).gradient(input).clone());
		}
		return m.times(1.0 / miniBatchList.size());
	}

	@Override
	public RiskFunction getCurrent() {
		// TODO Auto-generated method stub
		return this.funcs.get(index);
	}

	@Override
	public boolean innerLoop() {
		// TODO Auto-generated method stub
		return ((ChainedFunction) this.funcs.get(index)).innerLoop();
	}

	@Override
	public void toNext() {
		((ChainedFunction) this.getCurrent()).toNext();
	}
	
	@Override
	public MultiVariable project(MultiVariable input) {
		// TODO Auto-generated method stub
		return this.getCurrent().project(input);
	}
}
