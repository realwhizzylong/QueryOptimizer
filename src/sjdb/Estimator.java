package sjdb;

import java.util.List;

public class Estimator implements PlanVisitor {

	private int cost = 0;

	public Estimator() {

	}

	public void visit(Scan op) {
		Relation input = op.getRelation();
		Relation output = new Relation(input.getTupleCount());

		List<Attribute> list = input.getAttributes();
		for (Attribute attr : list) {
			output.addAttribute(new Attribute(attr));
		}

		op.setOutput(output);
		cost += output.getTupleCount();
	}

	public void visit(Project op) {
		Relation input = op.getInput().getOutput();

		// T(πA(R)) = T(R)
		Relation output = new Relation(input.getTupleCount());

		List<Attribute> aList = input.getAttributes();
		List<Attribute> pList = op.getAttributes();
		for (Attribute attr : pList) {
			if (aList.contains(attr)) {
				output.addAttribute(new Attribute(attr));
			}
		}

		op.setOutput(output);
		cost += output.getTupleCount();
	}

	public void visit(Select op) {
		Relation input = op.getInput().getOutput();
		Relation output = null;

		Predicate pred = op.getPredicate();
		Attribute leftAttr = pred.getLeftAttribute();

		if (pred.equalsValue()) {
			Attribute A = input.getAttribute(leftAttr);

			// T(σA=c(R)) = T(R)/V(R,A)
			output = new Relation(input.getTupleCount() / A.getValueCount());

			// V(σA=c(R), A) = 1
			Attribute ASelected = new Attribute(A.getName(), 1);

			List<Attribute> list = input.getAttributes();
			for (Attribute attr : list) {
				if (attr.equals(ASelected)) {
					output.addAttribute(new Attribute(ASelected));
				} else {
					output.addAttribute(new Attribute(attr));
				}
			}
		} else {
			Attribute rightAttr = pred.getRightAttribute();

			Attribute A = input.getAttribute(leftAttr);
			Attribute B = input.getAttribute(rightAttr);

			// T(σA=B(R)) = T(R)/max(V(R,A),V(R,B))
			output = new Relation(
					(int) Math.ceil(input.getTupleCount() / Math.max(A.getValueCount(), B.getValueCount())));

			// V(σA=B(R), A) = V(σA=B(R), B) = min(V(R, A), V(R, B)
			int value = Math.min(A.getValueCount(), B.getValueCount());
			Attribute ASelected = new Attribute(A.getName(), value);
			Attribute BSelected = new Attribute(B.getName(), value);

			List<Attribute> list = input.getAttributes();
			for (Attribute attr : list) {
				if (attr.equals(ASelected)) {
					output.addAttribute(new Attribute(ASelected));
				} else if (attr.equals(BSelected)) {
					output.addAttribute(new Attribute(BSelected));
				} else {
					output.addAttribute(new Attribute(attr));
				}
			}
		}
		op.setOutput(output);
		cost += output.getTupleCount();
	}

	public void visit(Product op) {
		Relation leftInput = op.getLeft().getOutput();
		Relation rightInput = op.getRight().getOutput();

		// T(R × S) = T(R)T(S)
		Relation output = new Relation(leftInput.getTupleCount() * rightInput.getTupleCount());

		List<Attribute> list1 = leftInput.getAttributes();
		for (Attribute attr : list1) {
			output.addAttribute(new Attribute(attr));
		}

		List<Attribute> list2 = rightInput.getAttributes();
		for (Attribute attr : list2) {
			output.addAttribute(new Attribute(attr));
		}

		op.setOutput(output);
		cost += output.getTupleCount();
	}

	public void visit(Join op) {
		Relation leftInput = op.getLeft().getOutput();
		Relation rightInput = op.getRight().getOutput();

		Predicate pred = op.getPredicate();
		Attribute leftAttr = new Attribute(pred.getLeftAttribute());
		Attribute rightAttr = new Attribute(pred.getRightAttribute());

		Attribute A = leftInput.getAttribute(leftAttr);
		Attribute B = rightInput.getAttribute(rightAttr);

		// T(R⨝A=BS) = T(R)T(S)/max(V(R,A),V(S,B))
		Relation output = new Relation((int) Math.ceil(leftInput.getTupleCount() * rightInput.getTupleCount()
				/ Math.max(A.getValueCount(), B.getValueCount())));

		// V(R⨝A=BS, A) = V(R⨝A=BS, B) = min(V(R, A), V(S, B))
		int value = Math.min(A.getValueCount(), B.getValueCount());
		Attribute AJoined = new Attribute(A.getName(), value);
		Attribute BJoined = new Attribute(B.getName(), value);

		List<Attribute> list1 = leftInput.getAttributes();
		for (Attribute attr : list1) {
			if (attr.equals(AJoined)) {
				output.addAttribute(new Attribute(AJoined));
			} else {
				output.addAttribute(new Attribute(attr));
			}
		}
		List<Attribute> list2 = rightInput.getAttributes();
		for (Attribute attr : list2) {
			if (attr.equals(BJoined)) {
				output.addAttribute(new Attribute(BJoined));
			} else {
				output.addAttribute(new Attribute(attr));
			}
		}

		op.setOutput(output);
		cost += output.getTupleCount();
	}

	public int getCost() {
		return cost;
	}
}
