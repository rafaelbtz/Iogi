package iogi;

import iogi.exceptions.InvalidTypeException;
import iogi.exceptions.IogiException;
import iogi.exceptions.NoConstructorFoundException;
import iogi.parameters.Parameter;
import iogi.parameters.Parameters;
import iogi.reflection.ClassConstructor;
import iogi.reflection.Primitives;
import iogi.reflection.Target;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import com.google.common.base.Joiner;

public class ObjectInstantiator implements Instantiator<Object> {
	private Instantiator<Object> argumentInstantiator;
	
	public ObjectInstantiator(Instantiator<Object> argumentInstantiator) {
		this.argumentInstantiator = argumentInstantiator;
	}

	@Override
	public boolean isAbleToInstantiate(Target<?> target) {
		return !Primitives.isPrimitiveLike(target.getClassType()) && target.getClassType() != String.class;
	}

	public Object instantiate(Target<?> target, Parameters parameters) {
		signalErrorIfTargetIsAbstract(target);
		
		Parameters relevantParameters = parameters.relevantTo(target).strip();
		Set<ClassConstructor> candidateConstructors = target.classConstructors();  
		
		Set<ClassConstructor> matchingConstructors = relevantParameters.compatible(candidateConstructors);
		signalErrorIfNoMatchingConstructorFound(target, matchingConstructors, relevantParameters);
		
		ClassConstructor firstMatchingConstructor = matchingConstructors.iterator().next();
		
		Object object = firstMatchingConstructor.instantiate(argumentInstantiator, relevantParameters);
		populateRemainingAttributes(object, firstMatchingConstructor, relevantParameters);
		
		
		return object;
	}

	private <T> void signalErrorIfTargetIsAbstract(Target<T> target) {
		if (!target.isInstantiable())
			throw new InvalidTypeException("Cannot instantiate abstract type %s", target.getClassType());
	}

	private <T> void signalErrorIfNoMatchingConstructorFound(Target<?> target, Set<ClassConstructor> matchingConstructors, Parameters relevantParameters) {
		if (matchingConstructors.isEmpty()) {
			String parameterList =  "(" + Joiner.on(", ").join(relevantParameters.getParametersList()) + ")";
			throw new NoConstructorFoundException("No constructor found to instantiate a %s named %s " +
					"with parameter names %s",
					target.getClassType(), target.getName(), parameterList);
		}
	}
	
	private void populateRemainingAttributes(Object object, ClassConstructor constructor, Parameters parameters) {
		try {
			BeanInfo beanInfo = Introspector.getBeanInfo(object.getClass());
			PropertyDescriptor[] beanProperties = beanInfo.getPropertyDescriptors();
			Parameters remainingParameters = parameters.notUsedBy(constructor);
			
			for (PropertyDescriptor property : beanProperties) {
				String propertyName = property.getName();
				Target<?> target = Target.create(property.getPropertyType(), propertyName);
				Parameter parameterNamedAfterProperty = remainingParameters.namedAfter(target);
				if (parameterNamedAfterProperty != null) {
					Object argument = argumentInstantiator.instantiate(target, remainingParameters);
					setArgument(object, property, argument);
				}
			}
			
		} catch (IntrospectionException e) {
			throw new IogiException(e);
		}
	}

	private Object setArgument(Object object, PropertyDescriptor property, Object argument) {
		try {
			return property.getWriteMethod().invoke(object, argument);
		} catch (IllegalArgumentException e) {
			throw new IogiException(e);
		} catch (IllegalAccessException e) {
			throw new IogiException(e);
		} catch (InvocationTargetException e) {
			throw new IogiException(e);
		}
	}
}
