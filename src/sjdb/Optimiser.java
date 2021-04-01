package sjdb;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class Optimiser implements PlanVisitor {

	private Catalogue catalogue;
	private Set<Scan> scans;
	private Set<Attribute> attributes;
	private Set<Predicate> predicates;
	private Estimator est;

	public Optimiser() {

	}

	public Optimiser(Catalogue catalogue) {
		this.catalogue = catalogue;
		scans = new HashSet<>();
		attributes = new HashSet<>();
		predicates = new HashSet<>();
		est = new Estimator();
	}

	@Override
	public void visit(Scan op) {
		scans.add(new Scan((NamedRelation) op.getRelation()));
	}

	@Override
	public void visit(Project op) {
		attributes.addAll(op.getAttributes());
	}

	@Override
	public void visit(Select op) {
		Predicate pred = op.getPredicate();
		predicates.add(pred);
		attributes.add(pred.getLeftAttribute());
		if (!pred.equalsValue()) {
			attributes.add(pred.getRightAttribute());
		}
	}

	@Override
	public void visit(Product op) {

	}

	@Override
	public void visit(Join op) {

	}

	public Operator optimise(Operator plan) {
		plan.accept(this);

		List<Operator> operators = seleAndProjDown(plan);

		Operator optPlan = reorder(plan, operators);

		return optPlan;
	}

	private List<Operator> seleAndProjDown(Operator plan) {
		List<Operator> list = new ArrayList<>();

		for (Scan scan : scans) {
			Operator select = implSelect(scan);

			List<Predicate> preds = new ArrayList<>();
			preds.addAll(predicates);
			Set<Attribute> attrs = getAttributes(plan, preds);

			Operator project = implProject(select, attrs);

			list.add(project);
		}

		return list;
	}

	private Operator reorder(Operator plan, List<Operator> opers) {
		Operator minPlan = null;

		List<List<Predicate>> result = new ArrayList<>();
		List<Predicate> preds = new ArrayList<>();
		preds.addAll(predicates);
		boolean[] used = new boolean[preds.size()];
		permutate(preds, used, result, new ArrayList<Predicate>());

		int minCost = Integer.MAX_VALUE;

		for (List<Predicate> list : result) {
			Operator tempPlan = implJoin(plan, list, opers);
			tempPlan.accept(est);
			int tempCost = est.getCost();
			if (tempCost < minCost) {
				minPlan = tempPlan;
				minCost = tempCost;
			}
		}

		return minPlan;
	}

	private Operator implSelect(Operator scan) {
		Operator operator = scan;

		if (scan.getOutput() == null) {
			operator.accept(est);
		}

		List<Attribute> list = scan.getOutput().getAttributes();

		Iterator<Predicate> it = predicates.iterator();
		while (it.hasNext()) {
			Predicate pred = it.next();
			if ((pred.equalsValue() && list.contains(pred.getLeftAttribute())) || (!pred.equalsValue()
					&& list.contains(pred.getLeftAttribute()) && list.contains(pred.getRightAttribute()))) {
				operator = new Select(scan, pred);
				it.remove();
			}
		}

		return operator;
	}

	private Operator implProject(Operator oper, Set<Attribute> attrs) {
		Operator operator = oper;

		if (oper.getOutput() == null) {
			operator.accept(est);
		}

		List<Attribute> operAttrs = oper.getOutput().getAttributes();
		List<Attribute> attrsForProj = new ArrayList<>();
		for (Attribute operAttr : operAttrs) {
			if (attrs.contains(operAttr)) {
				attrsForProj.add(operAttr);
			}
		}

		if (!attrsForProj.isEmpty()) {
			operator = new Project(oper, attrsForProj);
			operator.accept(est);
		}

		return operator;
	}

	private Operator implJoin(Operator plan, List<Predicate> preds, List<Operator> opers) {
		Operator operator = null;

		if (opers.size() == 1) {
			operator = opers.get(0);
			if (operator.getOutput() == null) {
				operator.accept(est);
			}
			return operator;
		}

		Iterator<Predicate> it = preds.iterator();
		while (it.hasNext()) {
			Predicate pred = it.next();

			Operator left = null, right = null;
			for (int i = 0; i < opers.size(); i++) {
				Operator oper = opers.get(i);
				if (oper.getOutput().getAttributes().contains(pred.getLeftAttribute())) {
					left = oper;
					opers.remove(oper);
					i--;
				} else if (oper.getOutput().getAttributes().contains(pred.getRightAttribute())) {
					right = oper;
					opers.remove(oper);
					i--;
				}
			}

			if (left != null && right != null) {
				operator = new Join(left, right, pred);
				it.remove();
			} else if (left != null) {
				operator = new Select(left, pred);
				it.remove();
			} else if (right != null) {
				operator = new Select(right, pred);
				it.remove();
			}

			if (operator.getOutput() == null) {
				operator.accept(est);
			}

			Set<Attribute> set = getAttributes(plan, preds);
			List<Attribute> list = operator.getOutput().getAttributes();
			if (list.size() == set.size() && list.containsAll(set)) {
				opers.add(operator);
			} else {
				List<Attribute> filterList = new ArrayList<>();
				for (Attribute attr : list) {
					if (set.contains(attr)) {
						filterList.add(attr);
					}
				}
				if (filterList.isEmpty()) {
					opers.add(operator);
				} else {
					Project project = new Project(operator, filterList);
					project.accept(est);
					opers.add(project);
				}
			}
		}

		while (opers.size() > 1) {
			Operator oper1 = opers.get(0);
			Operator oper2 = opers.get(1);
			Product product = new Product(oper1, oper2);
			product.accept(est);
			opers.add(product);
			opers.remove(oper1);
			opers.remove(oper2);
		}

		operator = opers.get(0);

		return operator;
	}

	private Set<Attribute> getAttributes(Operator plan, List<Predicate> preds) {
		Set<Attribute> set = new HashSet<>();

		for (Predicate pred : preds) {
			Attribute left = pred.getLeftAttribute();
			set.add(left);
			if (!pred.equalsValue()) {
				Attribute right = pred.getRightAttribute();
				set.add(right);
			}
		}

		if (plan instanceof Project) {
			set.addAll(((Project) plan).getAttributes());
		}

		return set;
	}

	private void permutate(List<Predicate> preds, boolean[] used, List<List<Predicate>> result, List<Predicate> list) {
		if (list.size() == preds.size()) {
			result.add(new ArrayList<>(list));
			return;
		}

		for (int i = 0; i < preds.size(); i++) {
			if (used[i]) {
				continue;
			}
			list.add(preds.get(i));
			used[i] = true;
			permutate(preds, used, result, list);
			list.remove(list.size() - 1);
			used[i] = false;
		}
	}

}
